# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

`yfinance-java`: a Java 21 port of the Python [`yfinance`](https://github.com/ranaroussi/yfinance) library, built on Retrofit 3 / OkHttp 5 / Jackson 2 with Gradle 9 (Kotlin DSL, version catalog in `gradle/libs.versions.toml`). It is a plain library with no framework dependencies. The package root is `io.github.dimazigel.yfinance`, and it publishes as `io.github.dimazigel:yfinance-java` (GitHub Packages only — Maven Central was considered and declined; see `RELEASING.md`). The public API is described in `README.md`.

## Commands

```bash
./gradlew test                    # unit tests (excludes @Tag("live")) + JaCoCo report
./gradlew test --tests 'io.github.dimazigel.yfinance.service.QuoteServiceTest'             # one class
./gradlew test --tests 'io.github.dimazigel.yfinance.service.QuoteServiceTest.parsesProfileAndQuote'  # one method
./gradlew integrationTest         # opt-in live suite against real Yahoo (src/integrationTest, @Tag("live"))
./gradlew build                   # compile (+Error Prone/NullAway/-Werror) + tests + Spotless check + coverage floor + jars; what CI runs
./gradlew spotlessApply           # fix import order / whitespace before committing
./gradlew publishToMavenLocal     # install 0.1.0-SNAPSHOT for consuming projects
```

Main code compiles with **Error Prone's default checks + NullAway + `-Werror`**, so any finding fails `compileJava`; test code is compiled without Error Prone. **Spotless** pins import order (static first, then one lexicographic block), unused imports, trailing whitespace and final newlines — `./gradlew spotlessApply` fixes, `check` verifies; indentation/wrapping are not enforced, match the surrounding code. `check` also enforces a **JaCoCo floor** of 85 % line / 60 % branch. Dependency scopes: `api` only for types in the public API (OkHttp, Retrofit, JSpecify); Jackson is `implementation` — don't put Jackson types in public signatures. The jar carries `Automatic-Module-Name: io.github.dimazigel.yfinance`. Configuration cache, parallel builds and build caching are enabled in `gradle.properties`, so custom Gradle tasks must stay configuration-cache compatible (see `VerifySourcesPublicationTask` in `build.gradle.kts`). Releases are cut by manually running the **Release** workflow (`.github/workflows/release.yml`) on `main`. It bumps the latest tag (patch/minor/major) or takes an explicit version, builds and tests, publishes to GitHub Packages, then creates the tag and GitHub release. Tags have no `v` prefix (`0.0.2`). `publish.yml` still publishes releases created by hand in the GitHub UI, skipping releases authored by `github-actions[bot]`. Both workflows run `.github/scripts/check-published.sh` first and skip publishing if the version already exists in GitHub Packages, so re-running a half-failed release is safe and nothing is ever uploaded twice. `live.yml` runs the live integration suite weekly (and on demand) to detect Yahoo API drift. Both workflows pass the version to Gradle as `-PreleaseVersion` and publish with the explicit `publishAllPublicationsToGitHubPackagesRepository` task. Plain `maven-publish`; the publication is named `mavenJava`.

## Architecture

Request flow: `YFinance` / `Ticker` / `Tickers` (facade) → `service/` → `api/` (Retrofit) → DTO → `mapper/` → `model/`.

- **`api/`**: one Retrofit interface per Yahoo endpoint. Methods return the **DTO type directly**, not `Call<T>`. That works because `http/SyncCallAdapterFactory` executes calls synchronously and turns HTTP and I/O failures into `YFDataException` / `YFRateLimitException`, including the error body and `Retry-After`. `YahooApis` bundles the interfaces. Fundamentals timeseries uses the **query2** host; everything else uses query1.
- **`dto/`**: raw records that mirror Yahoo's JSON. **`model/`**: clean, immutable public records. Keep the two separate, because callers should never see DTOs.
- **`service/`**: one service per concern. `HoldersService` and `AnalysisService` have no API of their own. They are built on top of `QuoteService`, since both read quoteSummary modules. `QuoteService` also owns `/v7/finance/quote` (`QuoteApi`): `getQuote`/`getQuotes` use it directly, and `getInfo` falls back to it when quoteSummary has no data for a symbol (common and inconsistent for non-equities), returning quote-only `Info`. The fallback never masks rate-limit or auth errors, and rethrows the quoteSummary error when the quote endpoint doesn't know the symbol either.
- **`http/`**: `YahooClientFactory` builds two OkHttp clients that share one cookie jar:
  - `baseClient` handles the auth handshake. It has no crumb interceptor, which avoids recursion.
  - `apiClient` runs the interceptor chain UserAgent → AuthRetry → TransientErrorRetry (5xx, `RetryConfig`) → AdaptiveRateLimit → Crumb. Anything that re-issues a request sits upstream of the rate limiter so retries are paced.
  - `AdaptiveRateLimiter` retries 429s up to `maxAttempts` and paces **every** request while degraded. It takes an injected clock and sleeper for tests.
  - `AuthRetryInterceptor` invalidates the crumb on 401/403 and retries once.
  - All tuning lives in the immutable `EndpointConfig` / `AdaptiveRateLimitConfig` records. Build configs as `EndpointConfig.production().with...()` (`withHosts(base)` for tests); there are no convenience constructors.
- **`auth/CrumbStore`**: gets a cookie from `fc.yahoo.com`, then a crumb from `/v1/test/getcrumb`, and caches it until invalidated.
- **`http/YahooObjectMapper` + `RawAwareNumberModule`**: quoteSummary returns some numbers as `{raw, fmt}` objects even with `formatted=false`. This module unwraps them globally for Long, Integer and BigDecimal. Don't add per-field workarounds.
- **`enums/`**: closed sets implement `WireEnum` (`wireValue()`). Resolve incoming values with `WireEnum.fromWire(values(), wire, label)`. `LineItem` gives type-safe keys for fundamentals.
- **`Tickers`**: `fetch(Function<Ticker, T>)` fans out any `Ticker` call across symbols with virtual threads and a `Semaphore` (`withConcurrency(n)`); `infos()`/`histories()` are shorthands. It returns `Map<Symbol, Tickers.Result<T>>` and never throws for an individual symbol. `Result` is a sealed interface (`Success`/`Failure` records) — prefer an exhaustive `switch` over `isSuccess()` checks; a fetcher returning `null` becomes a `Failure`, never a `Success` with a null value.
- **`YFinance.fromApis(YahooApis)`** skips the handshake. Use it for tests and advanced wiring.

## Conventions

- Use specific types in models: `BigDecimal` for money, `Instant`/`LocalDate`/`ZoneId` for time, `java.util.Currency`, `java.net.URI` for URLs, and value records such as `valueobject/Symbol`. Model types are records.
- Data-quality rules that tests enforce:
  - Missing values stay `null`. For example, `PriceBar.volume` is a nullable `Long` and is never coerced to 0.
  - Yahoo's all-null padding bars are dropped.
  - Collections in models are immutable.
  - Corporate-action dates use exchange-local dates via `localDate(zoneId)`.
  - Bars are raw OHLC + `adjClose`; `PriceBar.adjusted()`/`PriceHistory.adjusted()` give Python's `auto_adjust` view. Don't adjust silently.
- `MapperSupport` holds shared mapper helpers (`from`, `firstResult`, `epochSecond`). Reuse them.
- Development is test-first. Unit tests never touch the network: they enqueue captured JSON from `src/test/resources/fixtures/` on a `MockWebServer`, using `testsupport/Fixtures` (`Fixtures.api(server, XApi.class)`, `Fixtures.jsonResponse("name.json")`). A new endpoint or field normally needs a fixture, a service test, then the implementation.
- All errors are `YFinanceException` subtypes (`YFDataException`, `YFRateLimitException`, `YFAuthException`).
- **Nullness:** every package is JSpecify `@NullMarked` (see each `package-info.java`). Unannotated = never null; mark anything Yahoo may omit `@Nullable`. DTO components are all `@Nullable` by design. Record accessors inherit component nullness, so a component normalised in a compact constructor (lists, `events`) stays non-null in its declaration. Prefer restructuring code (locals, a small helper) over `@SuppressWarnings("NullAway")`.
- **Logging:** SLF4J (`LoggerFactory.getLogger(X.class)`); `slf4j-api` is an `api` dependency and the only logging dependency — never add a backend to main. Tests bind `slf4j-simple` at WARN (`src/test/resources/simplelogger.properties`). `INFO` for rare state changes worth an operator's attention, `WARN` for degraded behaviour, `DEBUG` for per-request detail. Use `{}` placeholders, not string concatenation.
- **HTTP client:** `EndpointConfig.clientCustomizer` must be applied last in `YahooClientFactory` so users can override anything the library sets.
