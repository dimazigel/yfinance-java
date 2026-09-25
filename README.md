# yfinance-java

[![Build](https://github.com/dimazigel/yfinance-java/actions/workflows/build.yml/badge.svg)](https://github.com/dimazigel/yfinance-java/actions/workflows/build.yml)

A clean, type-safe **Java 21** reimplementation of the Python
[`yfinance`](https://github.com/ranaroussi/yfinance) library, built on
**Retrofit 3** / OkHttp 5 / Jackson and **Gradle 9**.

It talks to Yahoo Finance's (undocumented) JSON endpoints and exposes the data
as immutable **records** with specific types — `BigDecimal` for money,
`Instant`/`LocalDate`/`ZoneId` for time, `java.util.Currency`, `java.net.URI`
for URLs, and value records like `Symbol`. Package root: `io.github.dimazigel.yfinance`.

**Requires Java 21+.** No framework dependencies — plain library, safe to use
from Spring, Quarkus, or a bare `main`.

## Quick start

```java
try (var yf = YFinance.create()) { // cookie+crumb handshake; close() releases the HTTP client
    var aapl = yf.ticker("AAPL");

    PriceHistory history = aapl.history(Range.ONE_MONTH, Interval.ONE_DAY);
    PriceHistory backfill = aapl.history(start, end, Interval.ONE_DAY); // explicit window
    List<Dividend> dividends = aapl.dividends();     // full-history corporate actions
    Info info           = aapl.info();               // info.quote().price() / .keyStats() / .analyst()
    Quote quote         = aapl.quote();              // lightweight, one request, any asset class
    FinancialStatement income = aapl.financials(StatementType.INCOME, Frequency.ANNUAL);
    OptionChain chain   = aapl.optionChain();
    Holders holders     = aapl.holders();            // incl. insiderRoster(), netSharePurchaseActivity()
    AnalystPriceTarget target = aapl.analystPriceTargets();
    List<EarningsHistoryEntry> beats = aapl.earningsHistory();
    List<EpsTrendPeriod> drift = aapl.epsTrend();    // also epsRevisions(), growthEstimates()
    List<NewsArticle> news = aapl.news();

    Map<Symbol, Quote> quotes = yf.quotes("AAPL", "^GSPC", "BTC-USD"); // one request for many symbols
    SearchResult results = yf.search("apple");
    List<LookupQuote> quotes = yf.lookup("apple", LookupType.EQUITY);
}
```

### Adjusted prices

Bars carry Yahoo's **raw** OHLC plus the split/dividend-adjusted close as `adjClose`. Python
yfinance defaults to adjusted OHLC (`auto_adjust=True`); to get the same numbers:

```java
PriceHistory adjusted = aapl.history(Range.MAX, Interval.ONE_DAY).adjusted(); // or bar.adjusted()
```

`adjusted()` scales open/high/low/close by `adjClose / close` and leaves volume as reported.

### Storing data: trading dates, typed line items, resilient batches

```java
// Corporate-action dates as the exchange's calendar date (not a UTC-shifted day):
for (Dividend d : history.dividends()) {
    LocalDate exDate = d.localDate(history.zoneId());
}

// Type-safe fundamental line items instead of magic strings:
BigDecimal revenue = income.value(LineItem.TOTAL_REVENUE, period);

// Fan out any Ticker call across symbols with bounded concurrency; one bad symbol never
// drops the rest. Result is sealed, so the switch is exhaustive without a default:
Map<Symbol, Tickers.Result<Info>> infos =
        yf.tickers("AAPL", "MSFT", "GOOG").withConcurrency(4).infos();
infos.forEach((symbol, result) -> {
    switch (result) {
        case Tickers.Result.Success<Info> ok -> store(symbol, ok.value());
        case Tickers.Result.Failure<Info> failed -> log.warn("skip {}: {}", symbol, failed.error().getMessage());
    }
});
Map<Symbol, Tickers.Result<OptionChain>> chains = yf.tickers("AAPL", "MSFT").fetch(Ticker::optionChain);
```

`PriceHistory.metadata()` also carries what Yahoo says about the instrument: the interval it
actually served (`dataGranularity()`), the `validRanges()` it accepts, today's pre/regular/post
sessions (`currentTradingPeriod()`), `regularMarketTime()` and `priceHint()`.

`YFinance` is thread-safe — hold one instance (e.g. a singleton) and `close()` it on shutdown.
The shared client adaptively throttles on HTTP 429: a throttled request is retried up to
`maxAttempts` times (waiting the adapted, jittered delay, honoring `Retry-After`), and while
degraded **every** request is paced by the current delay until traffic recovers — no burst-429
oscillation. Only after retries are exhausted is `YFRateLimitException` (with `retryAfter()`)
thrown. A stale crumb (401/403) is automatically invalidated and the request retried once. Transient
server errors (HTTP 500/502/503/504 — Yahoo's lookup endpoint is known to hiccup) are retried with
exponential backoff, honouring `Retry-After`: 3 attempts by default, tunable or disabled via
`EndpointConfig.withTransientRetry(RetryConfig)`.

`info()` on an instrument quoteSummary cannot describe (indices, ETFs, crypto, FX, futures) does
not fail: it falls back to `/v7/finance/quote` and returns quote-only info (`profile()` is `null`,
trend lists empty), mirroring Python yfinance. `quote()` / `quotes(...)` hit that endpoint directly.

Data-quality guarantees for storage pipelines: missing volume stays `null` (never coerced to 0),
Yahoo's all-null padding bars are dropped, and `FinancialStatement` collections are immutable.
30m history is fetched as 15m and resampled (Yahoo has been known to return 60m bars for 30m
requests; Python yfinance applies the same workaround).

## Configuration

Everything is tuned through `EndpointConfig` (an immutable record with `with...` copies):

```java
var config = EndpointConfig.production()
        .withCallTimeout(Duration.ofSeconds(10))
        .withAdaptiveRateLimit(new AdaptiveRateLimitConfig(
                true,                      // enabled
                Duration.ofMillis(500),    // initialDelay after the first 429
                Duration.ofSeconds(30),    // maxDelay cap
                2.0,                       // backoffMultiplier per consecutive 429
                0.5,                       // recoveryFactor per success while degraded
                0.2,                       // jitterFactor (±20% on scheduled waits)
                3));                       // maxAttempts per request (1 = never retry a 429)

try (var yf = YFinance.create(config)) {
    // ...
}
```

Derive variants from `production()` with `withHosts(...)`, `withUserAgent(...)`, `withCallTimeout(...)`,
`withAdaptiveRateLimit(...)`, `withTransientRetry(...)` and `withClientCustomizer(...)`.
`AdaptiveRateLimitConfig.defaults()` is what `EndpointConfig.production()` uses;
`AdaptiveRateLimitConfig.disabled()` turns throttling and 429-retries off entirely.

To customise the underlying OkHttp clients (proxy, extra interceptors, metrics, connection pool),
supply a customizer; it runs last, after the library's own interceptors and timeouts:

```java
var config = EndpointConfig.production()
        .withClientCustomizer(b -> b
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.corp", 8080)))
                .addInterceptor(myMetricsInterceptor));
```

### Nullability

The public API is annotated with [JSpecify](https://jspecify.dev): every package is `@NullMarked`,
so an unannotated type is never null and anything Yahoo may omit is `@Nullable`. IDEs, Kotlin and
NullAway pick this up automatically. The annotations are verified by NullAway on every build.

### Logging

The library logs through `java.lang.System.Logger`, so it needs no logging dependency and routes to
SLF4J, Log4j or `java.util.logging` automatically when one is present. Loggers are named after the
classes under `io.github.dimazigel.yfinance`. At `INFO` you see the rate limiter entering and leaving degraded
mode; at `WARNING`, degraded authentication (cookie or crumb unavailable); at `DEBUG`, individual
waits, crumb refreshes and auth retries.

## What's covered

| Area | Endpoint | API |
|---|---|---|
| Price history, dividends, splits, capital gains, metadata | `/v8/finance/chart` | `Ticker.history(...)`, `dividends()`, `splits()` |
| Company info, quote, recommendations, upgrades/downgrades, calendar, SEC filings | `/v10/finance/quoteSummary` | `Ticker.info()` |
| Lightweight quotes, single or batched, every asset class; also the `info()` fallback for indices/ETFs/crypto/FX/futures | `/v7/finance/quote` | `Ticker.quote()`, `YFinance.quotes(...)` |
| Income / balance sheet / cash flow (annual + quarterly) | `/ws/fundamentals-timeseries` | `Ticker.financials(...)` |
| Holders, insider transactions, insider roster, net purchase activity | `/v10/finance/quoteSummary` | `Ticker.holders()` |
| Analyst price targets, earnings/revenue estimates, earnings history, EPS trend/revisions, growth | `/v10/finance/quoteSummary` | `Ticker.analystPriceTargets()`, `earningsEstimate()`, `earningsHistory()`, `epsTrend()`, ... |
| Options chain | `/v7/finance/options` | `Ticker.optionChain(...)` |
| Search & per-symbol news | `/v1/finance/search` | `YFinance.search(...)`, `Ticker.news()` |
| Lookup | `/v1/finance/lookup` | `YFinance.lookup(...)` |

Deferred (the package layout leaves room for them): live WebSocket streaming,
`EquityQuery`/`Screener`, `Sector`/`Industry`, funds data, parallel downloads.

## Architecture

```
api/        Retrofit interfaces (one per endpoint) + YahooApis bundle
dto/        raw records mirroring Yahoo's JSON shape
model/      clean, public domain records
mapper/     DTO -> model conversion
service/    one service per concern (APIs return DTOs via SyncCallAdapterFactory)
auth/       CrumbStore — cookie (fc.yahoo.com) then crumb handshake, invalidate-on-401/403
http/       client factory, interceptors (UA, crumb, auth-retry, adaptive rate limit), ObjectMapper
enums/      closed sets implementing WireEnum (Interval, Range, ... )
valueobject/ Symbol, Crumb
YFinance / Ticker / Tickers — the facade
```

## Building, testing, consuming

```bash
./gradlew test                # fast, deterministic unit tests (MockWebServer + JSON fixtures) + JaCoCo
./gradlew integrationTest     # opt-in: hits the real Yahoo Finance API (@Tag("live"))
                              # also runs weekly in CI (.github/workflows/live.yml) to catch API drift
./gradlew build               # compile + unit tests + Spotless check + coverage floor + assemble jar
./gradlew spotlessApply       # fix import order / whitespace
./gradlew publishToMavenLocal # install io.github.dimazigel:yfinance-java:0.1.0-SNAPSHOT locally
```

### Consuming

Coordinates: **`io.github.dimazigel:yfinance-java:<version>`** — pick the version from the
[releases page](https://github.com/dimazigel/yfinance-java/releases). (Releases up to 0.0.2 were
published under the old `io.ziggy` group and package root; from the next release on, both are
`io.github.dimazigel`.)

Releases are published to **GitHub Packages**, which requires authentication even for public
packages: a GitHub username plus a [personal access token](https://github.com/settings/tokens)
with the `read:packages` scope.

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/dimazigel/yfinance-java")
        credentials {
            username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).get()
            password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).get()
        }
    }
}
dependencies { implementation("io.github.dimazigel:yfinance-java:<version>") }
```

The library is deliberately not published to Maven Central; GitHub Packages is the only
distribution channel. See [RELEASING.md](RELEASING.md) for how releases are cut.

Unit tests never touch the network; they replay hand-written JSON fixtures from
`src/test/resources/fixtures/` that mirror Yahoo's response shapes. The live suite
(`src/integrationTest`) verifies shape against real responses; it is excluded from `build`
and runs weekly in CI so that Yahoo API drift shows up as a failed run.

CI (GitHub Actions, `.github/workflows/build.yml`) runs `./gradlew build` on every
push/PR and uploads the JaCoCo coverage report as an artifact; CodeQL scans on every push, PR and
weekly. The build compiles main code with Error Prone, NullAway and `-Werror`, so a nullness mistake
or an Error Prone finding is a compile error. The Gradle
configuration cache is enabled via `gradle.properties`.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

## Error handling

All failures surface as `YFinanceException` subtypes:

| Exception | Meaning |
|---|---|
| `YFDataException` | Yahoo error envelope, unexpected HTTP status (body included in message), or I/O failure |
| `YFRateLimitException` | HTTP 429 after all adaptive retries; carries `retryAfter()` when Yahoo sent it |
| `YFAuthException` | The cookie/crumb handshake failed |

Batch calls via `Tickers` never throw per-symbol — each symbol yields a sealed
`Tickers.Result`: a `Success` holding the value or a `Failure` holding the exception
(`orElseThrow()` and `toOptional()` are available on both).

> Note: Yahoo Finance has no public/supported API. This library mirrors what the
> Python `yfinance` project does and is for personal/research use; endpoints and
> response shapes can change without notice.
