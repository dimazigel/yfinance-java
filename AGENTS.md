# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

`yfinance-java`: a Java 21 port of the Python [`yfinance`](https://github.com/ranaroussi/yfinance) library, built on Retrofit 3 / OkHttp 5 / Jackson 2 with Gradle 9 (Kotlin DSL, version catalog in `gradle/libs.versions.toml`). It is a plain library with no framework dependencies. The package root is `io.ziggy.yfinance`, and it publishes as `io.ziggy:yfinance-java`. The public API is described in `README.md`.

## Commands

```bash
./gradlew test                    # unit tests (excludes @Tag("live")) + JaCoCo report
./gradlew test --tests 'io.ziggy.yfinance.service.QuoteServiceTest'             # one class
./gradlew test --tests 'io.ziggy.yfinance.service.QuoteServiceTest.parsesProfileAndQuote'  # one method
./gradlew integrationTest         # opt-in live suite against real Yahoo (src/integrationTest, @Tag("live"))
./gradlew build                   # compile + unit tests + jars (what CI runs); excludes integrationTest
./gradlew publishToMavenLocal     # install 0.1.0-SNAPSHOT for consuming projects
```

There is no linter or formatter configured. Configuration cache, parallel builds and build caching are enabled in `gradle.properties`, so custom Gradle tasks must stay configuration-cache compatible (see `VerifySourcesPublicationTask` in `build.gradle.kts`). The publish workflow runs on GitHub release and uses `-PreleaseVersion=<tag without v>`.

## Architecture

Request flow: `YFinance` / `Ticker` / `Tickers` (facade) → `service/` → `api/` (Retrofit) → DTO → `mapper/` → `model/`.

- **`api/`**: one Retrofit interface per Yahoo endpoint. Methods return the **DTO type directly**, not `Call<T>`. That works because `http/SyncCallAdapterFactory` executes calls synchronously and turns HTTP and I/O failures into `YFDataException` / `YFRateLimitException`, including the error body and `Retry-After`. `YahooApis` bundles the interfaces. Fundamentals timeseries uses the **query2** host; everything else uses query1.
- **`dto/`**: raw records that mirror Yahoo's JSON. **`model/`**: clean, immutable public records. Keep the two separate, because callers should never see DTOs.
- **`service/`**: one service per concern. `HoldersService` and `AnalysisService` have no API of their own. They are built on top of `QuoteService`, since both read quoteSummary modules.
- **`http/`**: `YahooClientFactory` builds two OkHttp clients that share one cookie jar:
  - `baseClient` handles the auth handshake. It has no crumb interceptor, which avoids recursion.
  - `apiClient` runs the interceptor chain UserAgent → AdaptiveRateLimit → AuthRetry → Crumb.
  - `AdaptiveRateLimiter` retries 429s up to `maxAttempts` and paces **every** request while degraded. It takes an injected clock and sleeper for tests.
  - `AuthRetryInterceptor` invalidates the crumb on 401/403 and retries once.
  - All tuning lives in the immutable `EndpointConfig` / `AdaptiveRateLimitConfig` records, which have `with...` copy methods.
- **`auth/CrumbStore`**: gets a cookie from `fc.yahoo.com`, then a crumb from `/v1/test/getcrumb`, and caches it until invalidated.
- **`http/YahooObjectMapper` + `RawAwareNumberModule`**: quoteSummary returns some numbers as `{raw, fmt}` objects even with `formatted=false`. This module unwraps them globally for Long, Integer and BigDecimal. Don't add per-field workarounds.
- **`enums/`**: closed sets implement `WireEnum` (`wireValue()`). Resolve incoming values with `WireEnum.fromWire(values(), wire, label)`. `LineItem` gives type-safe keys for fundamentals.
- **`Tickers`**: fans out across symbols with virtual threads and a `Semaphore` (`withConcurrency(n)`). It returns `Map<Symbol, Tickers.Result<T>>` and never throws for an individual symbol.
- **`YFinance.fromApis(YahooApis)`** skips the handshake. Use it for tests and advanced wiring.

## Conventions

- Use specific types in models: `BigDecimal` for money, `Instant`/`LocalDate`/`ZoneId` for time, `java.util.Currency`, `java.net.URI` for URLs, and value records such as `valueobject/Symbol`. Model types are records.
- Data-quality rules that tests enforce:
  - Missing values stay `null`. For example, `PriceBar.volume` is a nullable `Long` and is never coerced to 0.
  - Yahoo's all-null padding bars are dropped.
  - Collections in models are immutable.
  - Corporate-action dates use exchange-local dates via `localDate(zoneId)`.
- `MapperSupport` holds shared mapper helpers (`from`, `firstResult`, `epochSecond`). Reuse them.
- Development is test-first. Unit tests never touch the network: they enqueue captured JSON from `src/test/resources/fixtures/` on a `MockWebServer`, using `testsupport/Fixtures` (`Fixtures.api(server, XApi.class)`, `Fixtures.jsonResponse("name.json")`). A new endpoint or field normally needs a fixture, a service test, then the implementation.
- All errors are `YFinanceException` subtypes (`YFDataException`, `YFRateLimitException`, `YFAuthException`).
