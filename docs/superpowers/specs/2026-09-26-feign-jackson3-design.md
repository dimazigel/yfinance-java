# Design: replace Retrofit with OpenFeign, upgrade Jackson to 3

Date: 2026-09-26. Status: approved by the owner (Approach A). Base: `main` after PR #32 (typed instrument model).

## 1. Goal and non-goals

Replace Retrofit 3 with OpenFeign 13 as the HTTP-interface layer and move from Jackson 2.22 to Jackson 3.2, without changing what the library does: same endpoints, same exception contract, same resilience chain, same public model.

Non-goals: dropping OkHttp (the interceptor chain, cookie jar and `CrumbStore` are the library's tested resilience layer and `EndpointConfig` exposes OkHttp types — Approach B was declined), touching the model/assembly semantics, adding Feign features (retryer, request interceptors, metrics).

## 2. Decisions

- **D1 — Feign runs on the existing OkHttp client** (`io.github.openfeign:feign-okhttp`). `YahooClientFactory.apiClient(config)` still builds the client with the chain UserAgent → LogContext → AuthRetry → TransientErrorRetry → AdaptiveRateLimit → RequestLog → Crumb; Feign is handed that client and adds nothing in front of it.
- **D2 — Feign never retries** (`Retryer.NEVER_RETRY`). Retries and pacing remain the interceptors' job (5xx, 429, 401/403). A Feign retry would sit outside the rate limiter.
- **D3 — The exception contract is unchanged** and lives in `http/`: 429 → `YFRateLimitException(retryAfter)`; other non-2xx → `YFHttpException(status, path, message)` with Yahoo's envelope `description` or an "HTML error page (N bytes)" summary appended; I/O failure → `YFDataException("I/O error calling Yahoo Finance", cause)`; empty 2xx body or malformed JSON → `YFDataException`. Messages keep today's wording (tests assert them).
- **D4 — Jackson 3 via `JsonMapper`**, coordinates `tools.jackson:jackson-bom` / `tools.jackson.core:jackson-databind`; java.time support is built in, so `jackson-datatype-jsr310` is dropped; annotations stay on `com.fasterxml.jackson.annotation` (Jackson 3 keeps them), so DTOs are untouched.
- **D5 — Dependency scopes**: Feign modules are `implementation` (no Feign type appears in a public signature; the `@RequestLine`/`@Param` annotations on the `api/` interfaces are not needed to compile against them); OkHttp stays `api` (`EndpointConfig.clientCustomizer`, `HttpUrl` hosts); Jackson stays `implementation` (internal `assembly`/`http` types expose `JsonNode` and are documented as internal).
- **D6 — Public API**: `YFinance.create*`, `YFinance.fromApis(YahooApis)`, `YahooApis.create(EndpointConfig, OkHttpClient)`, `EndpointConfig`, `YahooClientFactory` keep their signatures. `YahooApis.create(Retrofit, Retrofit)` and `http/SyncCallAdapterFactory` are removed. `http/YahooObjectMapper` is renamed `http/YahooJsonMapper` (returns `tools.jackson.databind.json.JsonMapper`). Breaking, acceptable: the library is unreleased.

## 3. Components

### 3.1 `api/` interfaces (8 files)

Retrofit annotations become Feign's. Query parameters are expressed in the request line template; a `@Param` whose value is `null` is omitted from the query string (Feign's template expansion), matching today's `@Nullable @Query` behaviour.

```java
public interface ChartApi {
    @RequestLine("GET /v8/finance/chart/{symbol}?interval={interval}&range={range}&period1={period1}"
            + "&period2={period2}&includePrePost={includePrePost}&events={events}")
    ChartResponse chart(@Param("symbol") String symbol, @Param("interval") String interval,
            @Param("range") @Nullable String range, @Param("period1") @Nullable Long period1,
            @Param("period2") @Nullable Long period2, @Param("includePrePost") boolean includePrePost,
            @Param("events") @Nullable String events);
}
```

Same mapping for `QuoteApi.quoteRows`, `QuoteSummaryApi.modules`, `OptionsApi.options`, `SearchApi.search`, `LookupApi.lookup`, `FundamentalsApi.timeseries` (the latter targets the query2 host). Path variables are URL-encoded by Feign; `^GSPC`, `EURUSD=X`, `BTC-USD` must round-trip — pinned by tests.

### 3.2 `api/YahooApis`

```java
public static YahooApis create(EndpointConfig config, OkHttpClient client) {
    JsonMapper mapper = YahooJsonMapper.create();
    Feign.Builder feign = Feign.builder()
            .client(new YahooFeignClient(new feign.okhttp.OkHttpClient(client)))
            .decoder(new YahooDecoder(mapper))
            .errorDecoder(new YahooErrorDecoder())
            .retryer(Retryer.NEVER_RETRY)
            .logLevel(Logger.Level.NONE);
    String q1 = config.query1Base().toString(), q2 = config.query2Base().toString();
    return new YahooApis(
            feign.target(ChartApi.class, q1), feign.target(QuoteSummaryApi.class, q1),
            feign.target(QuoteApi.class, q1), feign.target(FundamentalsApi.class, q2),
            feign.target(OptionsApi.class, q1), feign.target(SearchApi.class, q1),
            feign.target(LookupApi.class, q1));
}
```

`Feign.Builder` is reusable across targets. Base URLs are `HttpUrl.toString()` without a trailing slash; templates start with `/`.

### 3.3 `http/YahooFeignClient` (new, package-private)

A `feign.Client` decorator: delegates to `feign.okhttp.OkHttpClient`; catches `IOException` and rethrows `YFDataException("I/O error calling Yahoo Finance", e)`. Feign's method handler catches only `IOException` (to consult the retryer), so the unchecked exception propagates to the caller unchanged — this is what makes D2 airtight.

### 3.4 `http/YahooDecoder` (new, package-private)

Delegates to `feign.jackson3.Jackson3Decoder(mapper)`. Before delegating: `response.body() == null` or `Content-Length: 0` → `YFDataException("Yahoo Finance returned an empty body")`. Wraps `tools.jackson.core.JacksonException` (unchecked in Jackson 3) into `YFDataException("Yahoo Finance returned malformed JSON for <path>", e)`.

### 3.5 `http/YahooErrorDecoder` (new, package-private)

Feign calls it for every non-2xx response. Port of `SyncCallAdapterFactory.execute`'s error branches and helpers (`errorDetail`, `yahooErrorDescription`, `parseRetryAfter`): `path` = the request URL's raw path; 429 → `YFRateLimitException("Yahoo Finance rate limit hit (HTTP 429) for " + path + detail, retryAfter)`; else `YFHttpException(status, path, "Yahoo Finance returned HTTP " + status + " for " + path + detail)`. Body reading is tolerant (read failure → no detail). The envelope parser uses the shared `JsonMapper`.

### 3.6 `http/YahooJsonMapper` (renamed from `YahooObjectMapper`)

```java
public static JsonMapper create() {
    return JsonMapper.builder()
            .addModule(new RawAwareNumberModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            .build();
}
```

### 3.7 `http/RawAwareNumberModule` (ported)

`SimpleModule` with `ValueDeserializer<T>` (Jackson 3: `deserialize(JsonParser, DeserializationContext)` declares no checked exception; tree read via `ctxt.readTree(p)`). Semantics unchanged: `{raw, fmt}` unwrapped; `null`, `{raw: null}`, blank text → `null`; numbers via `decimalValue()`/`asLong()`/`asInt()`; non-numeric text parsed with `new BigDecimal(text)`.

### 3.8 Import migration

`com.fasterxml.jackson.databind.*` → `tools.jackson.databind.*` (`JsonNode`, `ObjectMapper`→`JsonMapper` where constructed, `node.MissingNode`, `DeserializationFeature`, `module.SimpleModule`, `ValueDeserializer`), `com.fasterxml.jackson.core.JacksonException`/`JsonParser` → `tools.jackson.core.*`. Known Jackson 3 API differences to watch: `JsonNode.fields()` is gone (code already uses `properties()`); `asText(default)`/`asString(default)` remain lenient; `ObjectMapper.readTree(String)` remains on `JsonMapper`; `JsonNode.isEmpty()`/`isMissingNode()` unchanged. Annotations (`@JsonIgnoreProperties`, `@JsonAnySetter`) untouched. **Correction (final review, finding 2):** the no-arg coercing accessors (`asLong()`, `asInt()`, `asText()`/`asString()`, `asBoolean()`, …) are no longer lenient in Jackson 3 — a non-coercible node (e.g. `asLong()` on `"abc"` or on an object/array node) throws `JsonNodeException` instead of returning `0`/`""`/`false` as in 2.x; only the explicit-default overloads (`asText(String)`/`asString(String)`, etc.) keep the old lenient contract. `asText()`/`isTextual()`/`textValue()` are deprecated aliases for `asString()`/`isString()`/`stringValue()`, same throw-on-failure semantics either way.

## 4. Data flow (unchanged)

`YFinance` → `service/*` → `api/*` (Feign proxies) → `YahooFeignClient` → OkHttp chain → Yahoo. Responses: 2xx → `YahooDecoder` → DTO or `JsonNode`; non-2xx → `YahooErrorDecoder` → `YF*Exception`; I/O → `YahooFeignClient` → `YFDataException`. `RawQuoteClient` (assembler entry) is unchanged except imports.

## 5. Errors

Contract per D3, one table, pinned by `YahooErrorDecoderTest`/`YahooApisTest`:

| condition | exception | message shape |
|---|---|---|
| HTTP 429 | `YFRateLimitException(retryAfter)` | `Yahoo Finance rate limit hit (HTTP 429) for <path>: <description>` |
| other non-2xx, JSON envelope | `YFHttpException(status, path)` | `Yahoo Finance returned HTTP <status> for <path>: <description>` |
| other non-2xx, HTML | `YFHttpException` | `…: HTML error page (<N> bytes)` |
| I/O failure | `YFDataException` | `I/O error calling Yahoo Finance` (cause attached) |
| 2xx empty body | `YFDataException` | `Yahoo Finance returned an empty body` |
| 2xx malformed JSON | `YFDataException` | `Yahoo Finance returned malformed JSON for <path>` (cause attached) |

`AuthRetryInterceptor` (401/403), `TransientErrorRetryInterceptor` (5xx) and `AdaptiveRateLimitInterceptor` (429) act before Feign ever sees the response, exactly as before.

## 6. Dependencies

Remove: `com.squareup.retrofit2:retrofit`, `converter-jackson`, `com.fasterxml.jackson:jackson-bom`, `jackson-databind` (2.x), `jackson-datatype-jsr310`.
Add: `io.github.openfeign:feign-bom:13.15` (platform), `feign-core`, `feign-okhttp`, `feign-jackson3` — `implementation`; `tools.jackson:jackson-bom:3.2.3` (platform), `tools.jackson.core:jackson-databind` — `implementation`. `feign-jackson3` pulls Jackson 3.2.2; the BOM pins 3.2.3. OkHttp BOM 5.5.0 unchanged (`feign-okhttp` depends on `okhttp-jvm` 5.5.0).

Error Prone / NullAway: Feign proxies are generated at run time; the `api/` interfaces stay `@NullMarked` with `@Nullable` on optional params. `feign.Client`/`Decoder`/`ErrorDecoder` implementations must satisfy NullAway (Feign's own types are unannotated → treat as unannotated third-party code; no `@SuppressWarnings` expected).

## 7. Testing

- **Unit (MockWebServer, fixture-first as today).** `testsupport.Fixtures.retrofit(...)` → `Fixtures.apis(MockWebServer, okhttp3.Interceptor...)` returning `YahooApis` built through `YahooApis.create(EndpointConfig.production().withHosts(base), client)`, and `Fixtures.api(server, XApi.class)` keeps working on top of it (so service tests change imports only).
- `SyncCallAdapterFactoryTest` → `YahooErrorDecoderTest` + `YahooApisTest`: the six rows of §5, plus request-shape tests: path variable encoding for `^GSPC`, `EURUSD=X`, `BTC-USD`, `005930.KS`; null `@Param` omitted (`range` absent when `period1/period2` given, `date` absent for options, `events` absent); boolean/long params rendered as before; `FundamentalsApi` hits the query2 host.
- `RawAwareNumberModuleTest` ported to Jackson 3 (same cases).
- `YahooClientFactoryTest`/interceptor tests: unchanged (they test OkHttp interceptors directly).
- Coverage floor 85/60 stays; Spotless import order handles the new packages.
- **Live**: `./gradlew integrationTest` once, green (the suite is unchanged in behaviour); `GuaranteeDriftTest` remains the drift guard.

## 8. Docs

README: stack line (OpenFeign 13 / OkHttp 5 / Jackson 3), the "customise the underlying OkHttp clients" section stays valid, the layout table (`api/` = Feign interfaces), the errors table unchanged. AGENTS.md: architecture bullets (`api/` Feign `@RequestLine` interfaces; `http/YahooFeignClient`/`YahooDecoder`/`YahooErrorDecoder` replace `SyncCallAdapterFactory`; `YahooJsonMapper`), dependency-scope rule (Feign `implementation`), Jackson-3 package note (`tools.jackson.*` vs annotations). Project memory: stack change.

## 9. Removal

Delete `http/SyncCallAdapterFactory` and its test, `YahooApis.create(Retrofit, Retrofit)`, `Fixtures.retrofit`, Retrofit/Jackson 2 catalog entries. `grep -rn "retrofit2\|com.fasterxml.jackson.databind\|com.fasterxml.jackson.core\|com.fasterxml.jackson.datatype" src/` must be empty afterwards (only `com.fasterxml.jackson.annotation` remains).

## 10. Open items / risks

- Feign template expansion of `@Param` values containing `=` or `,` (`EURUSD=X`, module lists `price,summaryDetail`): Feign percent-encodes path and query values; Yahoo accepts `%3D`/`%2C` — verified by the live suite (EURUSD=X and multi-module quoteSummary calls are in it). If Yahoo rejected the encoding, the fallback is a `@Param(expander = …)` or `feign.template` literal — decided at implementation with evidence.
- **Correction (final review, finding 2):** Jackson 3 `JsonNode.asLong()/asInt()/asString()` throw on a non-coercible node instead of defaulting (2.x returned `0`/`""`/`false`). This is not the same as 2.x. Through `RawAwareNumberModule`, garbage numerics now fail loudly as a `YFDataException("malformed JSON …")` instead of silently becoming `0` — consistent with the never-coerce rule (§ conventions in AGENTS.md), and now pinned by `RawAwareNumberModuleTest`. `Resolved`/`Nodes` already parsed strictly for numerics via `isNumber() ? ... : Long.parseLong(...)`, so they were unaffected there, but their no-arg `asString()`/`asBoolean()` getters (`Resolved.string()`, `Resolved.bool()`, `Nodes.string()/optString()`) now throw `JsonNodeException` on a container node instead of returning `""`/`false`; `InstrumentService`/`DetailService` wrap any `RuntimeException` from assembly into `YFDataException("Failed to assemble …")`, so this never leaks past the service boundary as a `Failed` outcome.
- `feign-okhttp` 13.15 depends on `okhttp-jvm`; the project's OkHttp BOM is the same 5.5.0, so no version skew.
