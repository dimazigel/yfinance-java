# Feign + Jackson 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Retrofit 3 with OpenFeign 13 as the HTTP-interface layer and move from Jackson 2.22 to Jackson 3.2, keeping the endpoints, the exception contract, the OkHttp resilience chain and the public model unchanged.

**Architecture:** Feign proxies for the eight `api/` interfaces run on the existing OkHttp client (`feign-okhttp`); a configured `Feign.Builder` (`http/YahooFeign`) installs a `feign.Client` decorator that turns I/O failures into `YFDataException`, a Jackson 3 decoder wrapper, an error decoder that ports today's 429/non-2xx mapping, an `InvocationHandlerFactory` that unwraps Feign's `DecodeException` back into our exceptions, `Retryer.NEVER_RETRY`, and request options that mirror the client's timeouts so `feign-okhttp` never clones the client. Jackson 3 is a `JsonMapper` with the ported `{raw,fmt}` number module; java.time is built in.

**Tech Stack:** Java 21, Gradle 9 (Kotlin DSL, version catalog), OpenFeign 13.15 (`feign-core`, `feign-okhttp`, `feign-jackson3`), Jackson 3.2.3 (`tools.jackson.*`; annotations stay `com.fasterxml.jackson.annotation`), OkHttp 5.5.0 (unchanged), JUnit 5 + AssertJ + MockWebServer, Error Prone + NullAway `-Werror`, Spotless, JaCoCo 85/60.

**Spec:** `docs/superpowers/specs/2026-09-26-feign-jackson3-design.md`

## Global Constraints

- Versions: `io.github.openfeign:feign-bom:13.15` (`feign-core`, `feign-okhttp`, `feign-jackson3`), `tools.jackson:jackson-bom:3.2.3` + `tools.jackson.core:jackson-databind`; OkHttp BOM stays `5.5.0`; `jackson-datatype-jsr310` is dropped (java.time is built into Jackson 3).
- Scopes (spec D5): Feign modules and Jackson are `implementation`; OkHttp, JSpecify, slf4j-api stay `api`. No Feign type in any public signature; the `@RequestLine`/`@Param` annotations on the `api/` interfaces are the only Feign symbols visible to consumers.
- Feign never retries (spec D2): `retryer(Retryer.NEVER_RETRY)`; the OkHttp interceptor chain (AuthRetry, TransientErrorRetry, AdaptiveRateLimit) is the only retry/pacing path and is untouched.
- Exception contract and message wording unchanged (spec §5): 429 → `YFRateLimitException(retryAfter)` `"Yahoo Finance rate limit hit (HTTP 429) for <path><detail>"`; other non-2xx → `YFHttpException(status, path)` `"Yahoo Finance returned HTTP <status> for <path><detail>"` where `<detail>` is `": <description>"` from Yahoo's envelope, or `": HTML error page (<N> bytes)"`, or `": <raw body>"`, or empty; I/O → `YFDataException("I/O error calling Yahoo Finance", cause)`; empty 2xx body → `YFDataException("Yahoo Finance returned an empty body")`; malformed JSON → `YFDataException("Yahoo Finance returned malformed JSON for <path>", cause)`. `<path>` is the request URL's raw path (e.g. `/v8/finance/chart/AAPL`).
- Feign's `Request.Options` are built from the OkHttp client (`connectTimeoutMillis`, `readTimeoutMillis`, `followRedirects`) so `feign.okhttp.OkHttpClient` uses the configured client as is; `EndpointConfig.callTimeout` keeps governing the whole call.
- Public API (spec D6): `YFinance.create*`, `YFinance.fromApis(YahooApis)`, `YahooApis.create(EndpointConfig, OkHttpClient)`, `EndpointConfig`, `YahooClientFactory` keep their signatures; `YahooApis.create(Retrofit, Retrofit)`, `http/SyncCallAdapterFactory`, `http/YahooObjectMapper` are removed; `http/YahooJsonMapper.create()` returns `tools.jackson.databind.json.JsonMapper`.
- After Task 3: `grep -rn "retrofit2\|com\.fasterxml\.jackson\.databind\|com\.fasterxml\.jackson\.core\|com\.fasterxml\.jackson\.datatype" src/ build.gradle.kts gradle/libs.versions.toml` is empty (only `com.fasterxml.jackson.annotation` remains).
- Build gates stay: `./gradlew build` (Error Prone defaults + `-Werror` + NullAway on `compileJava`, Spotless, JaCoCo 85 % line / 60 % branch). Run `./gradlew spotlessApply` before every commit; Spotless orders imports lexicographically in one block (`feign.*`, `io.github.*`, `java.*`, `okhttp3.*`, `org.*`, `tools.jackson.*`).
- Every package stays `@NullMarked`; Feign's types are unannotated third-party code — implement its interfaces without `@SuppressWarnings("NullAway")`.
- Never probe Yahoo with raw `curl`; live verification is `./gradlew integrationTest` (at most twice per task that needs it).
- Commit after every task with the trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`; branch `feign-jackson3`; PR against `main` with a green `build`; merge only when the owner says so.

## Review Focus

1. A `null` optional parameter (`range`, `period1`, `period2`, `events`, `date`) must be **omitted** from the query string, not sent as `range=` or `range=null` — pinned in Task 1 (`YahooFeignTest.nullParamsAreOmittedFromTheQuery`) and Task 2 (`YahooApisTest.chartWithPeriodsSendsNoRange`).
2. Symbols with `^`, `=`, `.`, `-` in the **path** (`^GSPC`, `EURUSD=X`, `005930.KS`, `BTC-USD`) and comma lists in the **query** (`modules=price,summaryDetail`) must reach the server as the same values Retrofit sent — pinned in Task 1 (`pathAndQueryValuesRoundTrip`) and live in Task 4.
3. An I/O failure must surface as `YFDataException` after **exactly one** attempt (Feign's retryer must not add attempts on top of the interceptors) — pinned in Task 1 (`ioFailureIsADataExceptionAfterOneAttempt`).
4. An exception raised while decoding must reach the caller as our exception, not Feign's `DecodeException` wrapper — pinned in Task 1 (`emptyBodyIsADataException`, `malformedJsonIsADataException`).
5. The configured timeouts must apply: a slow response must fail within the client's read timeout, proving Feign did not clone the client with its own 60 s default — pinned in Task 1 (`clientTimeoutsAreHonoured`).

---

### Task 1: Feign boundary and Jackson 3 mapper (Feign and Jackson 3 added beside Retrofit and Jackson 2)

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts` (dependencies block only)
- Create: `src/main/java/io/github/dimazigel/yfinance/http/YahooJsonMapper.java`, `http/Jackson3RawAwareNumberModule.java` (temporary name — Task 3 renames it to `RawAwareNumberModule` once the Jackson 2 class is gone), `http/YahooFeign.java`, `http/YahooFeignClient.java`, `http/YahooDecoder.java`, `http/YahooErrorDecoder.java`, `http/YahooInvocationHandlerFactory.java`
- Test: `src/test/java/io/github/dimazigel/yfinance/http/YahooFeignTest.java`, `http/Jackson3RawAwareNumberModuleTest.java`

**Interfaces:**
- Consumes: `exception/YFDataException(String)`, `YFDataException(String, Throwable)`, `YFRateLimitException(String, @Nullable Duration)`, `YFHttpException(int status, String path, String message)`, `YFinanceException` (existing).
- Produces: `public final class YahooFeign { public static Feign.Builder builder(okhttp3.OkHttpClient client, JsonMapper mapper) }` (package `http`; Task 2's `YahooApis` calls it); `public final class YahooJsonMapper { public static JsonMapper create() }`; `public final class Jackson3RawAwareNumberModule extends tools.jackson.databind.module.SimpleModule`; package-private `YahooFeignClient implements feign.Client`, `YahooDecoder implements feign.codec.Decoder`, `YahooErrorDecoder implements feign.codec.ErrorDecoder`, `YahooInvocationHandlerFactory implements feign.InvocationHandlerFactory`.

- [ ] **Step 1: Add the dependencies (Jackson 2 and Retrofit stay until Task 3)**

`gradle/libs.versions.toml` — add versions and libraries:

```toml
[versions]
feign = "13.15"
jackson3 = "3.2.3"

[libraries]
feign-bom = { module = "io.github.openfeign:feign-bom", version.ref = "feign" }
feign-core = { module = "io.github.openfeign:feign-core" }
feign-okhttp = { module = "io.github.openfeign:feign-okhttp" }
feign-jackson3 = { module = "io.github.openfeign:feign-jackson3" }
jackson3-bom = { module = "tools.jackson:jackson-bom", version.ref = "jackson3" }
jackson3-databind = { module = "tools.jackson.core:jackson-databind" }
```

`build.gradle.kts` — inside `dependencies { … }`, after the existing Jackson 2 lines:

```kotlin
    implementation(platform(libs.feign.bom))
    implementation(libs.feign.core)
    implementation(libs.feign.okhttp)
    implementation(libs.feign.jackson3)
    implementation(platform(libs.jackson3.bom))
    implementation(libs.jackson3.databind)
```

Run: `./gradlew compileJava -q` → BUILD SUCCESSFUL (both Jackson generations coexist; they live in different packages).

- [ ] **Step 2: Write the failing tests**

`src/test/java/io/github/dimazigel/yfinance/http/Jackson3RawAwareNumberModuleTest.java` — the existing `RawAwareNumberModuleTest` cases on the Jackson 3 mapper:

```java
package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Jackson 3 port of the {@code {raw, fmt}} number normalisation; same cases as the Jackson 2 module. */
class Jackson3RawAwareNumberModuleTest {

    private final JsonMapper mapper = YahooJsonMapper.create();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Numbers(@Nullable Long count, @Nullable Integer small, @Nullable BigDecimal price) {}

    @Test
    void plainScalarsDeserializeNormally() {
        var n = mapper.readValue("{\"count\":52000000,\"small\":7,\"price\":190.5}", Numbers.class);
        assertThat(n.count()).isEqualTo(52_000_000L);
        assertThat(n.small()).isEqualTo(7);
        assertThat(n.price()).isEqualByComparingTo("190.5");
    }

    @Test
    void rawFmtObjectsAreUnwrapped() {
        var n = mapper.readValue(
                "{\"count\":{\"raw\":52000000,\"fmt\":\"52M\"},\"small\":{\"raw\":7,\"fmt\":\"7\"},"
                        + "\"price\":{\"raw\":2950000000000,\"fmt\":\"2.95T\"}}", Numbers.class);
        assertThat(n.count()).isEqualTo(52_000_000L);
        assertThat(n.small()).isEqualTo(7);
        assertThat(n.price()).isEqualByComparingTo("2950000000000");
    }

    @Test
    void missingRawOrNullRawIsNull() {
        var n = mapper.readValue(
                "{\"count\":{\"raw\":null,\"fmt\":\"N/A\"},\"small\":{\"fmt\":\"N/A\"},\"price\":{}}", Numbers.class);
        assertThat(n.count()).isNull();
        assertThat(n.small()).isNull();
        assertThat(n.price()).isNull();
    }

    @Test
    void nullAndBlankStringAreNull() {
        var n = mapper.readValue("{\"count\":null,\"small\":\"\",\"price\":\"   \"}", Numbers.class);
        assertThat(n.count()).isNull();
        assertThat(n.small()).isNull();
        assertThat(n.price()).isNull();
    }

    @Test
    void numericStringsAreParsed() {
        var n = mapper.readValue("{\"count\":\"42\",\"small\":\"3\",\"price\":\"1.25\"}", Numbers.class);
        assertThat(n.count()).isEqualTo(42L);
        assertThat(n.small()).isEqualTo(3);
        assertThat(n.price()).isEqualByComparingTo("1.25");
    }

    @Test
    void bigDecimalKeepsFullPrecision() {
        var n = mapper.readValue("{\"price\":0.003125744}", Numbers.class);
        assertThat(n.price()).isEqualTo(new BigDecimal("0.003125744"));
    }

    @Test
    void nonNumericTextFailsLoudlyForDecimals() {
        assertThatThrownBy(() -> mapper.readValue("{\"price\":\"abc\"}", Numbers.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void unknownPropertiesAndUnknownEnumsAreTolerated() {
        record WithEnum(@Nullable java.time.DayOfWeek day) {}
        assertThat(mapper.readValue("{\"count\":1,\"surprise\":true}", Numbers.class).count()).isEqualTo(1L);
        assertThat(mapper.readValue("{\"day\":\"FUNDAY\"}", WithEnum.class).day()).isNull();
    }
}
```

`src/test/java/io/github/dimazigel/yfinance/http/YahooFeignTest.java` — exercises the whole Feign boundary through a test-only interface, independent of `api/`:

```java
package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFHttpException;
import io.github.dimazigel.yfinance.exception.YFRateLimitException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The Feign boundary: request shape (path/query encoding, omitted nulls), the exception contract for
 * every failure class, and that the configured OkHttp client is used as is (timeouts, one attempt).
 */
class YahooFeignTest {

    interface ProbeApi {
        @RequestLine("GET /probe/{symbol}?range={range}&period1={period1}&modules={modules}&flag={flag}")
        JsonNode probe(@Param("symbol") String symbol, @Param("range") @Nullable String range,
                @Param("period1") @Nullable Long period1, @Param("modules") @Nullable String modules,
                @Param("flag") boolean flag);
    }

    private MockWebServer server;
    private final AtomicInteger attempts = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private ProbeApi api(OkHttpClient client) {
        return YahooFeign.builder(client, YahooJsonMapper.create()).target(ProbeApi.class, server.url("/").toString());
    }

    private OkHttpClient countingClient(Duration readTimeout) {
        return new OkHttpClient.Builder()
                .readTimeout(readTimeout)
                .addInterceptor(chain -> {
                    attempts.incrementAndGet();
                    return chain.proceed(chain.request());
                })
                .build();
    }

    private static HttpUrl sent(MockWebServer server) throws InterruptedException {
        return server.url(java.util.Objects.requireNonNull(server.takeRequest().getPath()));
    }

    @Test
    void pathAndQueryValuesRoundTrip() throws Exception {   // Review Focus 2
        server.enqueue(new MockResponse().setBody("{}"));
        server.enqueue(new MockResponse().setBody("{}"));
        var api = api(new OkHttpClient());

        api.probe("^GSPC", "1mo", null, "price,summaryDetail", true);
        HttpUrl first = sent(server);
        assertThat(first.pathSegments()).containsExactly("probe", "^GSPC");
        assertThat(first.queryParameter("range")).isEqualTo("1mo");
        assertThat(first.queryParameter("modules")).isEqualTo("price,summaryDetail");
        assertThat(first.queryParameter("flag")).isEqualTo("true");

        api.probe("EURUSD=X", null, 1700000000L, null, false);
        HttpUrl second = sent(server);
        assertThat(second.pathSegments()).containsExactly("probe", "EURUSD=X");
        assertThat(second.queryParameter("period1")).isEqualTo("1700000000");
    }

    @Test
    void nullParamsAreOmittedFromTheQuery() throws Exception {   // Review Focus 1
        server.enqueue(new MockResponse().setBody("{}"));
        api(new OkHttpClient()).probe("AAPL", null, null, null, false);
        HttpUrl url = sent(server);
        assertThat(url.queryParameterNames()).containsExactly("flag");
    }

    @Test
    void successfulBodyDecodesToJson() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"quoteResponse\":{\"result\":[{\"symbol\":\"AAPL\",\"regularMarketPrice\":{\"raw\":1.5}}]}}"));
        JsonNode node = api(new OkHttpClient()).probe("AAPL", null, null, null, true);
        assertThat(node.path("quoteResponse").path("result").path(0).path("symbol").asText()).isEqualTo("AAPL");
    }

    @Test
    void httpErrorMessageIncludesStatusPathAndBody() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("upstream boom details"));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isInstanceOf(YFHttpException.class)
                .hasMessage("Yahoo Finance returned HTTP 500 for /probe/AAPL: upstream boom details")
                .satisfies(e -> {
                    assertThat(((YFHttpException) e).status()).isEqualTo(500);
                    assertThat(((YFHttpException) e).path()).isEqualTo("/probe/AAPL");
                });
    }

    @Test
    void httpErrorWithYahooEnvelopeSurfacesItsDescription() {
        server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json")
                .setBody("{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\","
                        + "\"description\":\"No data found, symbol may be delisted\"}}}"));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("NOPE", null, null, null, true))
                .isInstanceOf(YFHttpException.class)
                .hasMessage("Yahoo Finance returned HTTP 404 for /probe/NOPE: No data found, symbol may be delisted");
    }

    @Test
    void htmlErrorPagesAreSummarisedNotQuoted() {
        var html = "<!doctype html public \"-//W3C//DTD HTML 4.01//EN\"><html><head><title>Yahoo! - Error report</title></head></html>";
        server.enqueue(new MockResponse().setResponseCode(500).setBody(html));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isInstanceOf(YFHttpException.class)
                .hasMessage("Yahoo Finance returned HTTP 500 for /probe/AAPL: HTML error page (" + html.length() + " bytes)");
    }

    @Test
    void rateLimitCarriesRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "12").setBody("slow down"));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isInstanceOf(YFRateLimitException.class)
                .hasMessage("Yahoo Finance rate limit hit (HTTP 429) for /probe/AAPL: slow down")
                .satisfies(e -> assertThat(((YFRateLimitException) e).retryAfter()).contains(Duration.ofSeconds(12)));
    }

    @Test
    void rateLimitWithoutHeaderHasEmptyRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("slow down"));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isInstanceOf(YFRateLimitException.class)
                .satisfies(e -> assertThat(((YFRateLimitException) e).retryAfter()).isEmpty());
    }

    @Test
    void emptyBodyIsADataException() {   // Review Focus 4
        server.enqueue(new MockResponse().setResponseCode(200));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isExactlyInstanceOf(YFDataException.class)
                .hasMessage("Yahoo Finance returned an empty body");
    }

    @Test
    void malformedJsonIsADataException() {   // Review Focus 4
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("<html>consent page</html>"));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isExactlyInstanceOf(YFDataException.class)
                .hasMessage("Yahoo Finance returned malformed JSON for /probe/AAPL")
                .hasCauseInstanceOf(tools.jackson.core.JacksonException.class);
    }

    @Test
    void ioFailureIsADataExceptionAfterOneAttempt() throws Exception {   // Review Focus 3
        var api = api(countingClient(Duration.ofSeconds(5)));
        server.shutdown();
        assertThatThrownBy(() -> api.probe("AAPL", null, null, null, true))
                .isExactlyInstanceOf(YFDataException.class)
                .hasMessage("I/O error calling Yahoo Finance")
                .hasCauseInstanceOf(java.io.IOException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void clientTimeoutsAreHonoured() {   // Review Focus 5
        server.enqueue(new MockResponse().setBody("{}").setBodyDelay(3, TimeUnit.SECONDS));
        var api = api(countingClient(Duration.ofMillis(300)));
        long start = System.nanoTime();
        assertThatThrownBy(() -> api.probe("AAPL", null, null, null, true)).isExactlyInstanceOf(YFDataException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
        assertThat(attempts).hasValue(1);
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew test --tests 'io.github.dimazigel.yfinance.http.YahooFeignTest' --tests 'io.github.dimazigel.yfinance.http.Jackson3RawAwareNumberModuleTest'`
Expected: compilation FAILS — `YahooFeign`, `YahooJsonMapper`, `Jackson3RawAwareNumberModule` do not exist.

- [ ] **Step 4: Implement the Jackson 3 mapper**

`src/main/java/io/github/dimazigel/yfinance/http/Jackson3RawAwareNumberModule.java`:

```java
package io.github.dimazigel.yfinance.http;

import java.math.BigDecimal;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Yahoo's quoteSummary endpoint returns some numeric fields as plain scalars and others as
 * {@code {"raw": <number>, "fmt": "..."}} objects, even with {@code formatted=false}. This module
 * registers lenient deserializers for {@link Long}, {@link Integer} and {@link BigDecimal} that
 * accept either form (unwrapping {@code raw}), so DTOs can declare clean numeric types.
 */
public final class Jackson3RawAwareNumberModule extends SimpleModule {

    public Jackson3RawAwareNumberModule() {
        super("yahoo-raw-aware-numbers");
        addDeserializer(Long.class, new RawAware<>(JsonNode::asLong));
        addDeserializer(Integer.class, new RawAware<>(JsonNode::asInt));
        addDeserializer(BigDecimal.class, new RawAware<>(Jackson3RawAwareNumberModule::toBigDecimal));
    }

    private static BigDecimal toBigDecimal(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText());
    }

    private static final class RawAware<T> extends ValueDeserializer<T> {
        private final Function<JsonNode, T> convert;

        private RawAware(Function<JsonNode, T> convert) {
            this.convert = convert;
        }

        @Override
        public @Nullable T deserialize(JsonParser p, DeserializationContext ctxt) {
            return fromNode(ctxt.readTree(p));
        }

        private @Nullable T fromNode(@Nullable JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isObject()) {
                JsonNode raw = node.get("raw");
                return raw == null || raw.isNull() ? null : convert.apply(raw);
            }
            if (node.isTextual() && node.asText().isBlank()) {
                return null;
            }
            return convert.apply(node);
        }
    }
}
```

`src/main/java/io/github/dimazigel/yfinance/http/YahooJsonMapper.java`:

```java
package io.github.dimazigel.yfinance.http;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Factory for the Jackson 3 {@link JsonMapper} used to deserialize Yahoo's JSON: java.time support is
 * built in, unknown properties and unknown enum values are tolerated, and {@code {raw, fmt}} numbers
 * are unwrapped by {@link Jackson3RawAwareNumberModule}.
 *
 * <p>Internal plumbing, public only because {@code api.YahooApis} lives in another package. Jackson
 * is an {@code implementation} dependency of this library, so referencing this class from consumer
 * code additionally requires Jackson on that code's compile classpath.
 */
public final class YahooJsonMapper {

    private YahooJsonMapper() {}

    public static JsonMapper create() {
        return JsonMapper.builder()
                .addModule(new Jackson3RawAwareNumberModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .build();
    }
}
```

Run: `./gradlew test --tests 'io.github.dimazigel.yfinance.http.Jackson3RawAwareNumberModuleTest'` → PASS (8 tests).

- [ ] **Step 5: Implement the Feign boundary**

`src/main/java/io/github/dimazigel/yfinance/http/YahooFeignClient.java`:

```java
package io.github.dimazigel.yfinance.http;

import feign.Client;
import feign.Request;
import feign.Response;
import io.github.dimazigel.yfinance.exception.YFDataException;
import java.io.IOException;

/**
 * Runs Feign requests on the library's OkHttp client and turns transport failures into
 * {@link YFDataException}. Feign's method handler only catches {@link IOException} (to consult its
 * retryer), so the unchecked exception thrown here reaches the caller untouched — which is what keeps
 * the OkHttp interceptors the single retry path.
 */
final class YahooFeignClient implements Client {

    private final Client delegate;

    YahooFeignClient(okhttp3.OkHttpClient client) {
        this.delegate = new feign.okhttp.OkHttpClient(client);
    }

    @Override
    public Response execute(Request request, Request.Options options) {
        try {
            return delegate.execute(request, options);
        } catch (IOException e) {
            throw new YFDataException("I/O error calling Yahoo Finance", e);
        }
    }
}
```

`src/main/java/io/github/dimazigel/yfinance/http/YahooDecoder.java`:

```java
package io.github.dimazigel.yfinance.http;

import feign.Response;
import feign.codec.Decoder;
import feign.jackson3.Jackson3Decoder;
import io.github.dimazigel.yfinance.exception.YFDataException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 decoding with the library's failure semantics: an empty 2xx body and malformed JSON are
 * {@link YFDataException}s. Feign wraps exceptions thrown here in a {@code DecodeException};
 * {@link YahooInvocationHandlerFactory} unwraps them again at the proxy boundary.
 */
final class YahooDecoder implements Decoder {

    private final Decoder delegate;

    YahooDecoder(JsonMapper mapper) {
        this.delegate = new Jackson3Decoder(mapper);
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        Object value;
        try {
            value = delegate.decode(response, type);   // null for a missing or zero-length body
        } catch (JacksonException e) {
            throw new YFDataException("Yahoo Finance returned malformed JSON for " + path(response), e);
        }
        if (value == null) {
            throw new YFDataException("Yahoo Finance returned an empty body");
        }
        return value;
    }

    static String path(Response response) {
        return URI.create(response.request().url()).getRawPath();
    }
}
```

`src/main/java/io/github/dimazigel/yfinance/http/YahooErrorDecoder.java` (port of `SyncCallAdapterFactory`'s error branches):

```java
package io.github.dimazigel.yfinance.http;

import feign.Response;
import feign.codec.ErrorDecoder;
import io.github.dimazigel.yfinance.exception.YFHttpException;
import io.github.dimazigel.yfinance.exception.YFRateLimitException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps every non-2xx response to the library's exceptions: 429 → {@link YFRateLimitException} with
 * {@code Retry-After}; anything else → {@link YFHttpException}. The message names the endpoint (path)
 * and appends Yahoo's error {@code description} when the body is its JSON envelope, a size summary
 * when it is an HTML error page, or the raw text otherwise.
 */
final class YahooErrorDecoder implements ErrorDecoder {

    private final JsonMapper mapper;

    YahooErrorDecoder(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        String path = YahooDecoder.path(response);
        String detail = errorDetail(response);
        if (response.status() == 429) {
            return new YFRateLimitException(
                    "Yahoo Finance rate limit hit (HTTP 429) for " + path + detail,
                    parseRetryAfter(first(response.headers().get("Retry-After"))));
        }
        return new YFHttpException(response.status(), path,
                "Yahoo Finance returned HTTP " + response.status() + " for " + path + detail);
    }

    private String errorDetail(Response response) {
        Response.Body body = response.body();
        if (body == null) {
            return "";
        }
        try (var in = body.asInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            if (text.isEmpty()) {
                return "";
            }
            if (text.startsWith("<")) {
                return ": HTML error page (" + text.length() + " bytes)"; // Yahoo's 5xx pages; markup is noise
            }
            return ": " + yahooErrorDescription(text).orElse(text);
        } catch (IOException e) {
            return "";
        }
    }

    /** Extracts {@code description} from an envelope like {@code {"chart":{"error":{...}}}}. */
    private Optional<String> yahooErrorDescription(String text) {
        try {
            for (JsonNode envelope : mapper.readTree(text)) {
                String description = envelope.path("error").path("description").asText("");
                if (!description.isBlank()) {
                    return Optional.of(description);
                }
            }
        } catch (JacksonException e) {
            // Not JSON: fall back to the raw body.
        }
        return Optional.empty();
    }

    private static @Nullable String first(@Nullable Collection<String> values) {
        return values == null || values.isEmpty() ? null : values.iterator().next();
    }

    /** Parses a {@code Retry-After} header expressed as a whole number of seconds. */
    private static @Nullable Duration parseRetryAfter(@Nullable String headerValue) {
        if (headerValue == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(headerValue.strip()));
        } catch (NumberFormatException e) {
            return null; // HTTP-date form is not supported; treat as absent
        }
    }
}
```

`src/main/java/io/github/dimazigel/yfinance/http/YahooInvocationHandlerFactory.java`:

```java
package io.github.dimazigel.yfinance.http;

import feign.FeignException;
import feign.InvocationHandlerFactory;
import feign.Target;
import feign.codec.DecodeException;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * The proxy boundary: Feign wraps any exception thrown by a decoder in a {@link DecodeException};
 * this unwraps the library's own exceptions again and turns any other Feign failure into a
 * {@link YFDataException}, so callers only ever see {@link YFinanceException}s.
 */
final class YahooInvocationHandlerFactory implements InvocationHandlerFactory {

    private final InvocationHandlerFactory delegate = new InvocationHandlerFactory.Default();

    @Override
    public InvocationHandler create(Target<?> target, Map<Method, MethodHandler> dispatch) {
        InvocationHandler handler = delegate.create(target, dispatch);
        return (proxy, method, args) -> {
            try {
                return handler.invoke(proxy, method, args);
            } catch (DecodeException e) {
                if (e.getCause() instanceof YFinanceException ours) {
                    throw ours;
                }
                throw new YFDataException("Yahoo Finance response could not be decoded: " + e.getMessage(), e);
            } catch (FeignException e) {
                throw new YFDataException("Yahoo Finance call failed: " + e.getMessage(), e);
            }
        };
    }
}
```

(If `InvocationHandlerFactory.create`'s declared parameter is the raw `Target`, match the declared signature exactly — `Target target` — so the override compiles; Error Prone's default checks do not flag raw types.)

`src/main/java/io/github/dimazigel/yfinance/http/YahooFeign.java`:

```java
package io.github.dimazigel.yfinance.http;

import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place Feign is configured. The builder runs on the library's OkHttp client (all resilience
 * lives in its interceptor chain), never retries on its own, decodes with Jackson 3, and maps every
 * failure to a {@link io.github.dimazigel.yfinance.exception.YFinanceException}. Request options mirror
 * the client's timeouts so {@code feign-okhttp} uses the configured client as is instead of cloning it.
 *
 * <p>Internal to the library — not API; may change without notice.
 */
public final class YahooFeign {

    private YahooFeign() {}

    public static Feign.Builder builder(OkHttpClient client, JsonMapper mapper) {
        return Feign.builder()
                .client(new YahooFeignClient(client))
                .options(new Request.Options(
                        client.connectTimeoutMillis(), TimeUnit.MILLISECONDS,
                        client.readTimeoutMillis(), TimeUnit.MILLISECONDS,
                        client.followRedirects()))
                .decoder(new YahooDecoder(mapper))
                .errorDecoder(new YahooErrorDecoder(mapper))
                .invocationHandlerFactory(new YahooInvocationHandlerFactory())
                .retryer(Retryer.NEVER_RETRY)
                .logLevel(Logger.Level.NONE);
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew test --tests 'io.github.dimazigel.yfinance.http.YahooFeignTest' --tests 'io.github.dimazigel.yfinance.http.Jackson3RawAwareNumberModuleTest'`
Expected: PASS (12 + 8). If `pathAndQueryValuesRoundTrip` shows Feign percent-encoding `^` in the path, the decoded `pathSegments()` assertion still holds — that is the point of asserting decoded values; if `nullParamsAreOmittedFromTheQuery` fails with `range=` present, stop and report (the spec's assumption about Feign's template expansion would be wrong).

- [ ] **Step 7: Full build and commit**

Run: `./gradlew spotlessApply build` → BUILD SUCCESSFUL (Retrofit and Jackson 2 are still present; nothing else changed).

```bash
git add gradle/libs.versions.toml build.gradle.kts src/main/java/io/github/dimazigel/yfinance/http src/test/java/io/github/dimazigel/yfinance/http
git commit -m "Feign boundary and Jackson 3 mapper beside Retrofit: YahooFeign, decoders, invocation handler, JsonMapper

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Cut the `api/` interfaces over to Feign and migrate every `JsonNode` to Jackson 3

**Files:**
- Modify: `src/main/java/io/github/dimazigel/yfinance/api/{ChartApi,QuoteApi,QuoteSummaryApi,OptionsApi,SearchApi,LookupApi,FundamentalsApi,YahooApis}.java`
- Modify (imports `com.fasterxml.jackson.databind.*` → `tools.jackson.databind.*`, `YahooObjectMapper` → `YahooJsonMapper`, `ObjectMapper` → `JsonMapper`): every remaining main file that imports Jackson 2 databind/core (`assembly/Payload`, `assembly/Resolved`, `assembly/Resolver`, `assembly/build/*` that touch `JsonNode`, `assembly/specs/*` if any, `service/InstrumentService`, `service/DetailService`, `http/RawQuoteClient`, `mapper/*` if any), and every test/integration file that does (`testsupport/InstrumentFixtures`, `testsupport/YahooDispatcher`, `assembly/ResolverTest`, `assembly/build/*Test`, `service/DetailServiceTest`, `CaptureOptionFixtures`, `CaptureInstrumentFixtures` if applicable)
- Modify: `src/test/java/io/github/dimazigel/yfinance/testsupport/Fixtures.java`; tests calling `Fixtures.retrofit(...)`: `TickersTest`, `TickerTest`, `YFinanceTest` (two sites), `service/HistoryServiceTest` (`requestsRunInsideALogContextScope`)
- Delete: `src/main/java/io/github/dimazigel/yfinance/http/SyncCallAdapterFactory.java`, `src/test/java/io/github/dimazigel/yfinance/http/SyncCallAdapterFactoryTest.java` (all its cases exist in `YahooFeignTest`)
- Test: `src/test/java/io/github/dimazigel/yfinance/api/YahooApisTest.java` (new)

**Interfaces:**
- Consumes: `YahooFeign.builder(OkHttpClient, JsonMapper)`, `YahooJsonMapper.create()` (Task 1).
- Produces: the eight `api/` interfaces with Feign annotations (same method names, parameter lists and return types as today; `JsonNode` is now `tools.jackson.databind.JsonNode`); `YahooApis.create(EndpointConfig, OkHttpClient)` (unchanged signature, Feign inside); test helpers `Fixtures.apis(MockWebServer server, okhttp3.Interceptor... interceptors): YahooApis` and the unchanged `Fixtures.api(MockWebServer, Class<T>)`.

- [ ] **Step 1: Write the failing request-shape test**

`src/test/java/io/github/dimazigel/yfinance/api/YahooApisTest.java`:

```java
package io.github.dimazigel.yfinance.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The eight Feign interfaces send the same requests the Retrofit ones did. */
class YahooApisTest {

    private MockWebServer server;
    private YahooApis apis;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        apis = Fixtures.apis(server);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private HttpUrl sent() throws InterruptedException {
        return server.url(Objects.requireNonNull(server.takeRequest().getPath()));
    }

    @Test
    void chartWithRangeSendsNoPeriods() throws Exception {
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));
        apis.chart().chart("AAPL", "1d", "1mo", null, null, false, "div,splits");
        HttpUrl url = sent();
        assertThat(url.encodedPath()).isEqualTo("/v8/finance/chart/AAPL");
        assertThat(url.queryParameterNames()).containsExactlyInAnyOrder("interval", "range", "includePrePost", "events");
        assertThat(url.queryParameter("events")).isEqualTo("div,splits");
    }

    @Test
    void chartWithPeriodsSendsNoRange() throws Exception {   // Review Focus 1
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));
        apis.chart().chart("^GSPC", "1d", null, 1700000000L, 1700086400L, true, null);
        HttpUrl url = sent();
        assertThat(url.pathSegments()).containsExactly("v8", "finance", "chart", "^GSPC");
        assertThat(url.queryParameterNames()).containsExactlyInAnyOrder("interval", "period1", "period2", "includePrePost");
        assertThat(url.queryParameter("period2")).isEqualTo("1700086400");
    }

    @Test
    void quoteRowsAndModulesKeepTheirQueryShape() throws Exception {
        server.enqueue(Fixtures.jsonResponse("instruments/v7_AAPL.json"));
        apis.quote().quoteRows("AAPL,EURUSD=X", false);
        HttpUrl v7 = sent();
        assertThat(v7.encodedPath()).isEqualTo("/v7/finance/quote");
        assertThat(v7.queryParameter("symbols")).isEqualTo("AAPL,EURUSD=X");
        assertThat(v7.queryParameter("formatted")).isEqualTo("false");

        server.enqueue(Fixtures.jsonResponse("instruments/qs_BTC_USD.json"));
        apis.quoteSummary().modules("BTC-USD", "price,summaryDetail", false, "finance.yahoo.com");
        HttpUrl qs = sent();
        assertThat(qs.pathSegments()).containsExactly("v10", "finance", "quoteSummary", "BTC-USD");
        assertThat(qs.queryParameter("modules")).isEqualTo("price,summaryDetail");
        assertThat(qs.queryParameter("corsDomain")).isEqualTo("finance.yahoo.com");
    }

    @Test
    void optionsOmitsDateWhenAbsent() throws Exception {
        server.enqueue(Fixtures.jsonResponse("options/options_AAPL.json"));
        apis.options().options("AAPL", null);
        assertThat(sent().queryParameterNames()).isEmpty();

        server.enqueue(Fixtures.jsonResponse("options/options_AAPL_1790726400.json"));
        apis.options().options("AAPL", 1790726400L);
        assertThat(sent().queryParameter("date")).isEqualTo("1790726400");
    }

    @Test
    void fundamentalsGoToTheSecondHost() throws Exception {
        var second = new MockWebServer();
        second.start();
        try {
            var config = EndpointConfig.production().withHosts(server.url("/"), second.url("/"), server.url("/"));
            var split = YahooApis.create(config, new OkHttpClient());
            second.enqueue(Fixtures.jsonResponse("timeseries_income_annual.json"));
            split.fundamentals().timeseries("AAPL", "annualTotalRevenue", 1600000000L, 1700000000L);
            var request = second.takeRequest();
            assertThat(request.getPath()).startsWith("/ws/fundamentals-timeseries/v1/finance/timeseries/AAPL?");
            assertThat(server.getRequestCount()).isZero();
        } finally {
            second.shutdown();
        }
    }
}
```

(`timeseries_income_annual.json` is the existing fundamentals fixture; the assertion only needs a 2xx JSON body.)

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'io.github.dimazigel.yfinance.api.YahooApisTest'`
Expected: compilation FAILS — `Fixtures.apis` does not exist.

- [ ] **Step 3: Rewrite the eight interfaces**

Replace the Retrofit annotations one file at a time. Templates start with `/`, path variables in braces, every query parameter listed in the template; `@Nullable` parameters keep their annotation.

```java
// api/ChartApi.java
package io.github.dimazigel.yfinance.api;

import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.dto.chart.ChartResponse;
import org.jspecify.annotations.Nullable;

public interface ChartApi {
    @RequestLine("GET /v8/finance/chart/{symbol}?interval={interval}&range={range}&period1={period1}"
            + "&period2={period2}&includePrePost={includePrePost}&events={events}")
    ChartResponse chart(
            @Param("symbol") String symbol,
            @Param("interval") String interval,
            @Param("range") @Nullable String range,
            @Param("period1") @Nullable Long period1,
            @Param("period2") @Nullable Long period2,
            @Param("includePrePost") boolean includePrePost,
            @Param("events") @Nullable String events);
}
```

```java
// api/QuoteApi.java
@RequestLine("GET /v7/finance/quote?symbols={symbols}&formatted={formatted}")
tools.jackson.databind.JsonNode quoteRows(@Param("symbols") String symbols, @Param("formatted") boolean formatted);

// api/QuoteSummaryApi.java
@RequestLine("GET /v10/finance/quoteSummary/{symbol}?modules={modules}&formatted={formatted}&corsDomain={corsDomain}")
JsonNode modules(@Param("symbol") String symbol, @Param("modules") String modules,
        @Param("formatted") boolean formatted, @Param("corsDomain") String corsDomain);

// api/OptionsApi.java
@RequestLine("GET /v7/finance/options/{symbol}?date={date}")
OptionChainResponse options(@Param("symbol") String symbol, @Param("date") @Nullable Long date);

// api/SearchApi.java
@RequestLine("GET /v1/finance/search?q={q}&quotesCount={quotesCount}&newsCount={newsCount}&enableFuzzyQuery={enableFuzzyQuery}")
SearchResponse search(@Param("q") String query, @Param("quotesCount") int quotesCount,
        @Param("newsCount") int newsCount, @Param("enableFuzzyQuery") boolean enableFuzzyQuery);

// api/LookupApi.java
@RequestLine("GET /v1/finance/lookup?query={query}&type={type}&start={start}&count={count}&formatted={formatted}&fetchPricingData={fetchPricingData}")
LookupResponse lookup(@Param("query") String query, @Param("type") String type, @Param("start") int start,
        @Param("count") int count, @Param("formatted") boolean formatted, @Param("fetchPricingData") boolean fetchPricingData);

// api/FundamentalsApi.java
@RequestLine("GET /ws/fundamentals-timeseries/v1/finance/timeseries/{symbol}?type={type}&period1={period1}&period2={period2}")
TimeseriesResponse timeseries(@Param("symbol") String symbol, @Param("type") String type,
        @Param("period1") long period1, @Param("period2") long period2);
```

Keep each file's existing Javadoc; imports become `feign.Param`, `feign.RequestLine` (and `tools.jackson.databind.JsonNode` where used).

- [ ] **Step 4: Rewrite `YahooApis`**

```java
package io.github.dimazigel.yfinance.api;

import feign.Feign;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.http.YahooFeign;
import io.github.dimazigel.yfinance.http.YahooJsonMapper;
import okhttp3.OkHttpClient;

/**
 * The Feign interfaces for Yahoo's endpoints, bundled so {@code YFinance} can be wired from one object.
 * Fundamentals timeseries is served by the query2 host; everything else by query1.
 */
public record YahooApis(
        ChartApi chart,
        QuoteSummaryApi quoteSummary,
        QuoteApi quote,
        FundamentalsApi fundamentals,
        OptionsApi options,
        SearchApi search,
        LookupApi lookup) {

    /** Builds all interfaces from the given client and host configuration. */
    public static YahooApis create(EndpointConfig config, OkHttpClient client) {
        Feign.Builder feign = YahooFeign.builder(client, YahooJsonMapper.create());
        String query1 = base(config.query1Base());
        String query2 = base(config.query2Base());
        return new YahooApis(
                feign.target(ChartApi.class, query1),
                feign.target(QuoteSummaryApi.class, query1),
                feign.target(QuoteApi.class, query1),
                feign.target(FundamentalsApi.class, query2),
                feign.target(OptionsApi.class, query1),
                feign.target(SearchApi.class, query1),
                feign.target(LookupApi.class, query1));
    }

    /** Feign joins {@code base + template}; the templates start with {@code /}, so the base must not end with one. */
    private static String base(okhttp3.HttpUrl url) {
        String s = url.toString();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
```

- [ ] **Step 5: Replace the test wiring**

`src/test/java/io/github/dimazigel/yfinance/testsupport/Fixtures.java` — drop the Retrofit imports and `retrofit(...)`; add:

```java
    /** All eight interfaces against {@code server}, with optional OkHttp interceptors (e.g. to observe MDC). */
    public static YahooApis apis(MockWebServer server, okhttp3.Interceptor... interceptors) {
        var client = new okhttp3.OkHttpClient.Builder();
        for (var interceptor : interceptors) {
            client.addInterceptor(interceptor);
        }
        var base = server.url("/");
        return YahooApis.create(EndpointConfig.production().withHosts(base, base, base), client.build());
    }

    public static <T> T api(MockWebServer server, Class<T> apiClass) {
        YahooApis apis = apis(server);
        Object api;
        if (apiClass == ChartApi.class) api = apis.chart();
        else if (apiClass == QuoteSummaryApi.class) api = apis.quoteSummary();
        else if (apiClass == QuoteApi.class) api = apis.quote();
        else if (apiClass == FundamentalsApi.class) api = apis.fundamentals();
        else if (apiClass == OptionsApi.class) api = apis.options();
        else if (apiClass == SearchApi.class) api = apis.search();
        else if (apiClass == LookupApi.class) api = apis.lookup();
        else throw new IllegalArgumentException("Not a Yahoo API interface: " + apiClass);
        return apiClass.cast(api);
    }
```

(Check `EndpointConfig.withHosts` overloads: use the three-argument form `(query1Base, query2Base, cookieUrl)` so both hosts point at the mock server.) Update the callers:

- `TickersTest:46`, `TickerTest:44`, `YFinanceTest:48` and `:178`: `yf = YFinance.fromApis(Fixtures.apis(server));` (remove the `retrofit` local).
- `HistoryServiceTest.requestsRunInsideALogContextScope`: `var api = Fixtures.apis(server, chain -> { … }).chart();`.

- [ ] **Step 6: Migrate the Jackson imports and the mapper factory**

Mechanical, across `src/main`, `src/test`, `src/integrationTest`:

```bash
grep -rl "com.fasterxml.jackson.databind\|com.fasterxml.jackson.core" src | sort
```

For every listed file: `com.fasterxml.jackson.databind.JsonNode` → `tools.jackson.databind.JsonNode`; `…databind.node.MissingNode` → `tools.jackson.databind.node.MissingNode`; `…databind.node.ObjectNode/ArrayNode` likewise; `com.fasterxml.jackson.databind.ObjectMapper` → `tools.jackson.databind.json.JsonMapper` and `new ObjectMapper()` → `JsonMapper.builder().build()`; `YahooObjectMapper.create()` → `YahooJsonMapper.create()`; `com.fasterxml.jackson.core.JacksonException` → `tools.jackson.core.JacksonException`; remove now-unneeded `throws IOException`/`JsonProcessingException` on test methods that only parsed JSON (Jackson 3 exceptions are unchecked; Error Prone does not run on tests, but unused `throws` clauses are fine to leave if the method also does I/O). Do **not** touch `com.fasterxml.jackson.annotation.*` imports. Leave `http/YahooObjectMapper`, `http/RawAwareNumberModule` and their test in place (Task 3 deletes them) — they still compile against Jackson 2.

Delete `http/SyncCallAdapterFactory.java` and `http/SyncCallAdapterFactoryTest.java` (`git rm`).

Check whether any DTO relies on the old `JavaTimeModule`: `grep -rn "Instant\|LocalDate\|OffsetDateTime" src/main/java/io/github/dimazigel/yfinance/dto` — expected: none (DTOs carry epoch numbers); if one exists, Jackson 3's built-in java.time support deserializes ISO strings and epoch numbers the same way `JavaTimeModule` did, and the fixture-backed service test for that DTO is the proof.

- [ ] **Step 7: Compile, fix the fallout, run the whole suite**

Run: `./gradlew compileJava compileTestJava compileIntegrationTestJava -q` and fix every error the migration surfaces (expected kinds: `JsonNode.fields()` → `properties()` if any remain; checked-exception handlers around `readTree` that are now unreachable — delete the `catch (JsonProcessingException …)` or widen to `JacksonException`; `ObjectMapper` variables typed `ObjectMapper` → `JsonMapper`). Then:

Run: `./gradlew spotlessApply test` → all green, including `YahooApisTest` (5), `YahooFeignTest`, the service tests over the real fixtures, and `compileIntegrationTestJava`.

- [ ] **Step 8: Full build and commit**

Run: `./gradlew build` → BUILD SUCCESSFUL (coverage floor included — `YahooFeignTest` covers the new `http` classes).

```bash
git add -A src gradle build.gradle.kts
git commit -m "Feign interfaces replace Retrofit; JsonNode and mappers move to Jackson 3

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Remove Retrofit and Jackson 2; final names

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`
- Delete: `src/main/java/io/github/dimazigel/yfinance/http/YahooObjectMapper.java`, `http/RawAwareNumberModule.java` (Jackson 2), `src/test/java/io/github/dimazigel/yfinance/http/RawAwareNumberModuleTest.java` (Jackson 2)
- Rename (`git mv`): `http/Jackson3RawAwareNumberModule.java` → `http/RawAwareNumberModule.java`, `http/Jackson3RawAwareNumberModuleTest.java` → `http/RawAwareNumberModuleTest.java` (class names follow)

**Interfaces:**
- Produces: final `http/RawAwareNumberModule` (Jackson 3) referenced by `YahooJsonMapper`; no Retrofit or Jackson 2 artifact on any classpath.

- [ ] **Step 1: Write the failing hygiene test**

`src/test/java/io/github/dimazigel/yfinance/http/DependencyHygieneTest.java`:

```java
package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Retrofit and Jackson 2 databind must be gone from the runtime classpath (annotations 2.x are expected). */
class DependencyHygieneTest {

    @Test
    void retrofitAndJackson2DatabindAreNotOnTheClasspath() {
        assertThat(present("retrofit2.Retrofit")).as("Retrofit").isFalse();
        assertThat(present("com.fasterxml.jackson.databind.ObjectMapper")).as("Jackson 2 databind").isFalse();
        assertThat(present("tools.jackson.databind.json.JsonMapper")).as("Jackson 3 databind").isTrue();
        assertThat(present("com.fasterxml.jackson.annotation.JsonIgnoreProperties")).as("Jackson annotations").isTrue();
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, DependencyHygieneTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

Run: `./gradlew test --tests 'io.github.dimazigel.yfinance.http.DependencyHygieneTest'` → FAILS (Retrofit and Jackson 2 still present).

- [ ] **Step 2: Remove the old dependencies and classes**

`gradle/libs.versions.toml`: delete `retrofit` and `jackson` from `[versions]`; delete `retrofit`, `retrofit-converter-jackson`, `jackson-bom`, `jackson-databind`, `jackson-datatype-jsr310` from `[libraries]`; rename `jackson3`/`jackson3-*` entries to `jackson`/`jackson-bom`/`jackson-databind` (one name, the current generation).

`build.gradle.kts` dependencies block becomes:

```kotlin
    // `api`: types that appear in the public API (HttpUrl/OkHttpClient.Builder in EndpointConfig,
    // JSpecify annotations everywhere). Feign and Jackson are implementation details: consumers see
    // them at runtime only (the Feign annotations on the api/ interfaces are not needed to compile).
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    api(libs.jspecify)
    api(libs.slf4j.api) // consumers bind their own backend; only the API is a dependency

    implementation(platform(libs.feign.bom))
    implementation(libs.feign.core)
    implementation(libs.feign.okhttp)
    implementation(libs.feign.jackson3)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
```

Delete `http/YahooObjectMapper.java`, `http/RawAwareNumberModule.java`, `http/RawAwareNumberModuleTest.java` (`git rm`); then `git mv http/Jackson3RawAwareNumberModule.java http/RawAwareNumberModule.java` (and the test), rename the classes and every reference (`YahooJsonMapper`, Javadoc).

- [ ] **Step 3: Verify and commit**

```bash
grep -rn "retrofit2\|com\.fasterxml\.jackson\.databind\|com\.fasterxml\.jackson\.core\|com\.fasterxml\.jackson\.datatype\|Jackson3RawAware\|YahooObjectMapper\|SyncCallAdapterFactory" src/ build.gradle.kts gradle/libs.versions.toml
./gradlew dependencies --configuration runtimeClasspath | grep -i "retrofit\|com.fasterxml.jackson.core:jackson-databind" || echo clean
./gradlew spotlessApply build
```

Expected: the grep prints nothing; the dependency report prints `clean`; BUILD SUCCESSFUL with `DependencyHygieneTest` green.

```bash
git add -A
git commit -m "Remove Retrofit and Jackson 2; RawAwareNumberModule is the Jackson 3 module

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Docs, live verification, PR

**Files:**
- Modify: `README.md` (stack line 7 → "**OpenFeign 13** / OkHttp 5 / Jackson 3 and **Gradle 9**"; the layout table line "api/ Retrofit interfaces …" → "api/ Feign interfaces (one per endpoint) + YahooApis bundle"; the "customise the underlying OkHttp clients" section stays; the error table is unchanged), `AGENTS.md` (line 7 stack; the request-flow bullet: `api/` (Feign `@RequestLine` interfaces); the `api/` bullet: methods return the response type directly — Feign proxies built by `http/YahooFeign` on the OkHttp client, `YahooFeignClient` maps I/O errors, `YahooDecoder`/`YahooErrorDecoder` map bodies and statuses, `YahooInvocationHandlerFactory` unwraps Feign's `DecodeException`, `Retryer.NEVER_RETRY`, request options mirror the client's timeouts; the `YahooObjectMapper` bullet → `YahooJsonMapper` + `RawAwareNumberModule` (Jackson 3, java.time built in); dependency-scope rule: Feign and Jackson `implementation`, OkHttp `api`; Jackson-3 package note: `tools.jackson.*` for databind/core, `com.fasterxml.jackson.annotation` for annotations)
- No source changes.

- [ ] **Step 1: Write the docs** as listed; then `grep -n "Retrofit\|retrofit\|Jackson 2\|SyncCallAdapter\|YahooObjectMapper\|converter-jackson" README.md AGENTS.md` → nothing left.

- [ ] **Step 2: Gates**

```bash
./gradlew spotlessApply build          # green
./gradlew integrationTest              # live, once; green — covers ^GSPC/EURUSD=X/BTC-USD paths and comma module lists (Review Focus 2)
```

If the live run fails on a request-shape assertion (Yahoo rejecting an encoding), stop and report the failing test and the request line — the spec's fallback (`@Param(expander = …)`) is a design change, not a docs fix.

- [ ] **Step 3: Commit, push, PR**

```bash
git add README.md AGENTS.md
git commit -m "Docs: OpenFeign and Jackson 3

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
env -u GITHUB_TOKEN git push -u origin feign-jackson3
env -u GITHUB_TOKEN gh pr create --base main --title "Replace Retrofit with OpenFeign; upgrade to Jackson 3" --body-file <body>
env -u GITHUB_TOKEN gh pr checks <n> --watch
```

PR body: what changed and why (spec D1–D6 in one line each), the unchanged exception contract (the §5 table), breaking changes (`YahooApis.create(Retrofit, Retrofit)`, `SyncCallAdapterFactory`, `YahooObjectMapper` removed; `YahooJsonMapper` added; Feign/Jackson 3 coordinates), the test plan (unit count, `YahooFeignTest` cases, live run result), and it ends with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. Print the bare PR URL. Do not merge.

---

## Self-review notes (run after writing; fixed inline)

- **Spec coverage:** D1/§3.2–3.3 → Tasks 1–2 (`YahooFeign`, `YahooFeignClient`, `YahooApis`); D2 → Task 1 (`NEVER_RETRY`, `ioFailureIsADataExceptionAfterOneAttempt`); D3/§5 → Task 1 (`YahooErrorDecoder`, `YahooDecoder`, `YahooInvocationHandlerFactory`, seven exception tests); D4/§3.6–3.7 → Task 1 (`YahooJsonMapper`, module port) and Task 3 (final name); D5/§6 → Tasks 1 and 3 (scopes, removals, hygiene test); D6/§9 → Tasks 2–3; §3.1 → Task 2; §3.8 → Task 2 Step 6; §7 → Tasks 1–2 tests, Task 4 live; §8 → Task 4; §10 encoding risk → Task 1 `pathAndQueryValuesRoundTrip` (decoded equality) + Task 4 live.
- **Type consistency:** `YahooFeign.builder(OkHttpClient, JsonMapper)` used identically in Task 1 tests and Task 2 `YahooApis`; `YahooJsonMapper.create(): JsonMapper` everywhere; `Fixtures.apis(MockWebServer, Interceptor...)`/`Fixtures.api(MockWebServer, Class<T>)` as declared in Task 2 and used by `YahooApisTest`, `TickersTest`, `TickerTest`, `YFinanceTest`, `HistoryServiceTest`; `Jackson3RawAwareNumberModule` exists only between Tasks 1 and 3.
- **Feign facts relied on (verified against feign-core 13.15 sources):** unresolved (null) template variables are removed from the query; `InvocationContext.decode` wraps decoder `RuntimeException`s in `DecodeException` (hence the invocation handler); `SynchronousMethodHandler.executeAndDecode` catches only `IOException` from the client (hence the unchecked exception in `YahooFeignClient` bypasses the retryer); `feign.okhttp.OkHttpClient.getClient` rebuilds the client when `Request.Options` differ from the client's connect/read timeouts or `followRedirects` (hence the mirrored options).
- **Review Focus:** each of the five lines names its pinning test; none is left without one.
