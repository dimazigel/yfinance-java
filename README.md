# yfinance-java

[![Build](https://github.com/dimazigel/yfinance-java/actions/workflows/build.yml/badge.svg)](https://github.com/dimazigel/yfinance-java/actions/workflows/build.yml)

A clean, type-safe **Java 21** reimplementation of the Python
[`yfinance`](https://github.com/ranaroussi/yfinance) library, built on
**Retrofit 3** / OkHttp 5 / Jackson and **Gradle 9**.

It talks to Yahoo Finance's (undocumented) JSON endpoints and exposes the data
as immutable **records** with specific types — `BigDecimal` for money,
`Instant`/`LocalDate`/`ZoneId` for time, `java.util.Currency`, `java.net.URI`
for URLs, and value records like `Symbol`. Package root: `io.ziggy.yfinance`.

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
    FinancialStatement income = aapl.financials(StatementType.INCOME, Frequency.ANNUAL);
    OptionChain chain   = aapl.optionChain();
    Holders holders     = aapl.holders();            // incl. insiderRoster(), netSharePurchaseActivity()
    AnalystPriceTarget target = aapl.analystPriceTargets();
    List<EarningsHistoryEntry> beats = aapl.earningsHistory();
    List<EpsTrendPeriod> drift = aapl.epsTrend();    // also epsRevisions(), growthEstimates()
    List<NewsArticle> news = aapl.news();

    SearchResult results = yf.search("apple");
    List<LookupQuote> quotes = yf.lookup("apple", LookupType.EQUITY);
}
```

### Storing data: trading dates, typed line items, resilient batches

```java
// Corporate-action dates as the exchange's calendar date (not a UTC-shifted day):
for (Dividend d : history.dividends()) {
    LocalDate exDate = d.localDate(history.zoneId());
}

// Type-safe fundamental line items instead of magic strings:
BigDecimal revenue = income.value(LineItem.TOTAL_REVENUE, period);

// Fan out across symbols with bounded concurrency; one bad symbol never drops the rest:
Map<Symbol, Tickers.Result<Info>> infos =
        yf.tickers("AAPL", "MSFT", "GOOG").withConcurrency(4).infos();
infos.forEach((symbol, result) -> {
    if (result.isSuccess()) store(symbol, result.value());
    else log.warn("skip {}: {}", symbol, result.error().getMessage());
});
```

`YFinance` is thread-safe — hold one instance (e.g. a singleton) and `close()` it on shutdown.
The shared client adaptively throttles on HTTP 429: a throttled request is retried up to
`maxAttempts` times (waiting the adapted, jittered delay, honoring `Retry-After`), and while
degraded **every** request is paced by the current delay until traffic recovers — no burst-429
oscillation. Only after retries are exhausted is `YFRateLimitException` (with `retryAfter()`)
thrown. A stale crumb (401/403) is automatically invalidated and the request retried once.

Data-quality guarantees for storage pipelines: missing volume stays `null` (never coerced to 0),
Yahoo's all-null padding bars are dropped, and `FinancialStatement` collections are immutable.

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

`AdaptiveRateLimitConfig.defaults()` is what `EndpointConfig.production()` uses;
`AdaptiveRateLimitConfig.disabled()` turns throttling and 429-retries off entirely.

## What's covered

| Area | Endpoint | API |
|---|---|---|
| Price history, dividends, splits, capital gains, metadata | `/v8/finance/chart` | `Ticker.history(...)`, `dividends()`, `splits()` |
| Company info, quote, recommendations, upgrades/downgrades, calendar, SEC filings | `/v10/finance/quoteSummary` | `Ticker.info()` |
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
./gradlew build               # compile + unit tests + assemble jar
./gradlew publishToMavenLocal # install io.ziggy:yfinance-java for consuming projects
```

Consume from another Gradle project (after `publishToMavenLocal`):

```kotlin
repositories { mavenLocal(); mavenCentral() }
dependencies { implementation("io.ziggy:yfinance-java:0.1.0-SNAPSHOT") }
```

Unit tests never touch the network; they replay captured fixtures from
`src/test/resources/fixtures/`. The live suite (`src/integrationTest`) verifies
shape against real responses and is excluded from `build`.

CI (GitHub Actions, `.github/workflows/build.yml`) runs `./gradlew build` on every
push/PR and uploads the JaCoCo coverage report as an artifact. The Gradle
configuration cache is enabled via `gradle.properties`.

## Error handling

All failures surface as `YFinanceException` subtypes:

| Exception | Meaning |
|---|---|
| `YFDataException` | Yahoo error envelope, unexpected HTTP status (body included in message), or I/O failure |
| `YFRateLimitException` | HTTP 429 after all adaptive retries; carries `retryAfter()` when Yahoo sent it |
| `YFAuthException` | The cookie/crumb handshake failed |

Batch calls via `Tickers` never throw per-symbol — each symbol yields a
`Tickers.Result` holding either the value or the exception.

> Note: Yahoo Finance has no public/supported API. This library mirrors what the
> Python `yfinance` project does and is for personal/research use; endpoints and
> response shapes can change without notice.
