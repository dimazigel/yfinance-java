# yfinance-java

[![Build](https://github.com/dimazigel/yfinance-java/actions/workflows/build.yml/badge.svg)](https://github.com/dimazigel/yfinance-java/actions/workflows/build.yml)

A type-safe **Java 21** reimplementation of the Python
[`yfinance`](https://github.com/ranaroussi/yfinance) library, built on
**OpenFeign 13** / OkHttp 5 / Jackson 3 and **Gradle 9**.

It talks to Yahoo Finance's (undocumented) JSON endpoints and exposes the data as immutable
**records** whose types state what Yahoo guarantees for each kind of instrument: an `Equity` has a
non-null `marketCap`, an `Index` has no such field, and anything Yahoo only sometimes reports is an
`Optional`. Values use specific types — `BigDecimal` for money, `Instant`/`LocalDate`/`ZoneId` for
time, `java.net.URI` for URLs, and value records like `Symbol` and `QuoteCurrency`. Package root:
`io.github.dimazigel.yfinance`.

**Requires Java 21+.** No framework dependencies — plain library, safe to use from Spring, Quarkus,
or a bare `main`.

## Quick start

```java
try (var yf = YFinance.create()) { // cookie+crumb handshake; close() releases the HTTP client
    Ticker aapl = yf.ticker("AAPL");

    // One snapshot; the sealed hierarchy makes the switch exhaustive without a default.
    Instrument instrument = aapl.instrument();
    String line = switch (instrument) {
        case Equity e -> e.symbol() + " market cap " + e.valuation().marketCap();
        case Etf etf -> etf.symbol() + " YTD " + etf.ytdReturn();
        case MutualFund mf -> mf.symbol() + " expense ratio " + mf.expenseRatio();
        case Index i -> i.symbol() + " " + i.core().price();
        case Crypto c -> c.symbol() + " circulating " + c.supply().circulating();
        case FxPair fx -> fx.symbol() + " " + fx.core().price();
        case Future f -> f.symbol() + " expires " + f.contract().expireDate();
        case Unclassified u -> u.symbol() + " not classified, missing " + u.missing();
    };

    Equity equity = aapl.as(Equity.class);      // YFClassMismatchException if AAPL were not an equity
    EquityDetail detail = yf.detail(equity);    // profile, statistics, financials, analysts, ownership
    PriceHistory history = aapl.history(Range.ONE_MONTH, Interval.ONE_DAY);
    List<Dividend> dividends = aapl.dividends();          // full-history corporate actions
    Optional<OptionChain> chain = aapl.options();          // empty when the instrument has no listed options
    FinancialStatement income = yf.statements(equity, StatementType.INCOME, Frequency.ANNUAL);
    Map<StatementType, Map<Frequency, FinancialStatement>> statements = yf.statements(equity,   // one request
            Set.of(StatementType.INCOME, StatementType.BALANCE_SHEET, StatementType.CASH_FLOW), Set.of(Frequency.ANNUAL, Frequency.QUARTERLY));
    Optional<BigDecimal> revenue = income.latest(LineItem.TOTAL_REVENUE);   // most recent period; row(...) for all periods
    List<NewsArticle> news = aapl.news();

    SearchResult results = yf.search("apple");
    List<LookupQuote> lookup = yf.lookup("apple", LookupType.EQUITY);
}
```

`Ticker` methods return a value or throw. `as(Class)` throws `YFClassMismatchException` (carrying
the actual `AssetClass`) when the symbol is something else, including `Unclassified`; `instrument()`
throws `YFMissingDataException` for a symbol Yahoo does not know. `detail(...)` and
`statements(...)` take the instrument itself as proof of its class, so the compiler stops you from
asking for a company profile of an index or the income statement of an ETF. The instrument in hand
is the natural argument: `yf.detail(equity)`, `yf.detail(etf)`, `yf.detail(fund)`, `yf.detail(crypto)`
and `yf.statements(equity, type, frequency)` take it directly (one request each). The `Ticker`
forms do the same after checking that the proof is for the ticker's own symbol.

### Batches

Every `YFinance` batch call is N in → N out: one `Outcome` per input symbol, in input order, never
throwing for an individual symbol. Duplicates in the input yield duplicate outcomes; an empty input
makes no request.

```java
List<Symbol> symbols = Stream.of("AAPL", "SPY", "VFIAX", "BTC-USD", "NOSUCHSYMBOL").map(Symbol::of).toList();

Batch<Instrument> batch = yf.instruments(symbols);   // one request per 100 symbols
for (Outcome<Instrument> outcome : batch) {          // Batch is Iterable<Outcome<T>>
    switch (outcome) {
        case Outcome.Ok<Instrument> ok -> store(ok.value());
        case Outcome.Skipped<Instrument> s -> log.info("{} skipped: {} ({})", s.symbol(), s.reason(), s.detail());
        case Outcome.Failed<Instrument> f -> retryLater(f.symbol(), f.error());
    }
}
log.info(batch.summary());                           // "5 symbols: 4 ok, 1 skipped, 0 failed"
Map<Symbol, Instrument> bySymbol = batch.toMap();    // Ok values only, input order, first wins on a duplicate
List<Instrument> all = batch.orElseThrowAll();        // all-or-nothing: throws the first skip/failure
Batch<BigDecimal> prices = batch.map(i -> i.core().price());   // Skipped/Failed pass through retyped

Batch<Equity> equities = yf.instruments(symbols, Equity.class);   // others → Skipped(WRONG_ASSET_CLASS)
Batch<EquityDetail> details = yf.equityDetails(equities.values()); // one quoteSummary request each
// Classify once, then fetch details: 1 v7 request per 100 symbols + 1 quoteSummary per equity.
// tickers(symbols).fetch(t -> t.detail(t.as(Equity.class))) gives the same outcomes but costs two
// requests per symbol, because each as(...) classifies its symbol alone instead of in the batch.
Batch<PriceHistory> histories = yf.histories(symbols, Range.ONE_YEAR, Interval.ONE_DAY);
Batch<Optional<OptionChain>> chains = yf.options(symbols);
Batch<FinancialStatement> statements = yf.statements(equities.values(), StatementType.INCOME, Frequency.ANNUAL);
// Several statements per equity in ONE timeseries request each (type → frequency → statement):
Batch<Map<StatementType, Map<Frequency, FinancialStatement>>> multi = yf.statements(equities.values(),
        Set.of(StatementType.INCOME, StatementType.CASH_FLOW), Set.of(Frequency.ANNUAL, Frequency.QUARTERLY));

// Fan out any Ticker call with bounded concurrency (virtual threads); a Ticker's non-answer
// (unknown symbol, wrong class for as(...), absent module) comes back as Skipped, not Failed:
Batch<List<Dividend>> dividends = yf.tickers("AAPL", "MSFT", "GOOG").withConcurrency(8).fetch(Ticker::dividends);
Batch<Equity> viaFetch = yf.tickers(symbols).fetch(t -> t.as(Equity.class));   // same outcomes as instruments(symbols, Equity.class)
```

`Outcome<T>` is sealed:

| Outcome | Meaning | Retry? |
|---|---|---|
| `Ok(symbol, value)` | the value | — |
| `Skipped(symbol, reason, detail)` | Yahoo answered, but there is nothing to return for this symbol | no |
| `Failed(symbol, error)` | transport, 429 after retries, 5xx after retries, malformed JSON — a `YFinanceException` | `isRetryable()`: yes for transport, 429 and 5xx; no for malformed data |

`SkipReason` is `UNKNOWN_SYMBOL` (not in Yahoo's quote response), `WRONG_ASSET_CLASS`
(`instruments(symbols, Equity.class)` met an ETF), `DOWNGRADED` (see below), `NOT_AVAILABLE_FOR_CLASS`
and `MODULE_ABSENT` (a detail request lacked a guaranteed module; `detail` names the fields).
`Outcome.Failed.isRetryable()` is `error().isRetryable()` (see the error table below), so a queue
consumer can re-enqueue without an `instanceof` chain.
`Batch` is `Iterable<Outcome<T>>` and offers `outcomes()`, `stream()`, `values()` (the `Ok` values
only), `ok()`, `skipped()`, `failed()`, `allOk()`, `toMap()` (`Ok` values by symbol, input order,
first wins on a duplicate; skipped and failed symbols are absent), `orElseThrowAll()` (every value,
or the first non-`Ok` outcome's exception), `map(fn)` (transforms the `Ok` values, passes the rest
through), `get(symbol)` (first occurrence, a linear scan) and `summary()`; each `Outcome` has
`optional()`, `map(fn)` and `orElseThrow()` (`Skipped` throws `YFSkippedException`, a
`YFMissingDataException` carrying the `SkipReason`; `Failed` rethrows its error). The mapping is symmetric: `Tickers.fetch` turns a `YFSkippedException` or a
`YFClassMismatchException` thrown inside the fetcher back into `Skipped`, so `fetch(t -> t.as(Equity.class))`
skips for the same reasons as `instruments(symbols, Equity.class)`.

### Price history

`HistoryQuery` is the general price-history request: a `Range` **or** an explicit `[start, end)`
period, plus `includePrePost` and `events`. `Ticker.history(Range, Interval)` and
`Ticker.history(start, end, Interval)` are shorthand for the common cases and build one under the
hood; reach for `HistoryQuery` directly for pre/post bars, a subset of corporate-action events, or
an open-ended period.

```java
// Range form — same as ticker.history(Range.ONE_MONTH, Interval.ONE_DAY).
PriceHistory month = aapl.history(HistoryQuery.range(Range.ONE_MONTH, Interval.ONE_DAY));

// Explicit window, pre/post-market bars, dividends only (no splits/capital gains).
PriceHistory extended = aapl.history(HistoryQuery.of(Interval.ONE_HOUR)
        .period(Instant.now().minus(Duration.ofDays(5)), Instant.now())
        .includePrePost(true)
        .events(EventType.DIVIDENDS)
        .build());

// Batch backfill: the same explicit window for every symbol, one Outcome each.
Batch<PriceHistory> backfill = yf.histories(symbols,
        HistoryQuery.period(Instant.now().minus(Duration.ofDays(30)), Instant.now(), Interval.ONE_DAY));
```

`HistoryQuery.of(interval)` starts a builder; `range(...)` and `period(...)` are mutually exclusive
(each clears the other) and one of them is required before `build()`. An open-ended
`period(start)` (no `end`) resolves to "now" at request time, not at build time.

`HistoryRequest` — the old, symbol-carrying request type `Ticker.history(HistoryRequest)` took — is
`@Deprecated(since = "1.2", forRemoval = true)`; `HistoryRequest.toQuery()` converts an existing
request. It will be removed in 2.0.

### Coming from Python yfinance

| Python `yfinance` | `yfinance-java` |
|---|---|
| `Ticker("AAPL").info` | `ticker.instrument()` (snapshot, typed by class) + `yf.detail(equity)` / `ticker.detail(equity)` (profile, statistics, analysts, ownership); two requests, because Yahoo's v7 quote and quoteSummary are two endpoints |
| `history(period="1mo", interval="1d")` | `ticker.history(Range.ONE_MONTH, Interval.ONE_DAY)`; `history(start, end, interval)` for a window; `history(HistoryQuery.of(interval)....build())` for `includePrePost`/`events` (see [Price history](#price-history)) |
| `history(auto_adjust=True)` (the Python default) | `ticker.history(...).adjusted()` — bars are raw OHLC + `adjClose` until you ask |
| `dividends` / `splits` / `actions` / `capital_gains` | `ticker.dividends()` / `ticker.splits()`; `history(...).dividends()` / `.splits()` / `.capitalGains()` on any fetched window |
| `options` / `option_chain(date)` | `ticker.options()` → `Optional<OptionChain>` (nearest expiration; `expirationDates()` lists the rest), `ticker.options(expiration)` for one of them |
| `financials` / `balance_sheet` / `cashflow` (+ `quarterly_*`, `ttm_*`) | `yf.statements(equity, StatementType.INCOME \| BALANCE_SHEET \| CASH_FLOW, Frequency.ANNUAL \| QUARTERLY \| TRAILING)`; several at once in one request: `yf.statements(equity, Set.of(...types), Set.of(...frequencies))` |
| `Tickers("AAPL MSFT")` / `download([...])` | `yf.instruments(symbols)` (one request per 100 symbols) / `yf.histories(symbols, range, interval)`; `yf.tickers(...).fetch(Ticker::...)` fans any call out |
| `Search("apple")` / `Lookup("apple")` | `yf.search("apple")` (`quotes()` + `news()`) / `yf.lookup("apple", LookupType.EQUITY)` |
| `Ticker.news` | `ticker.news()` |
| `history(repair=True)`, `EquityQuery`/`Screener`, `WebSocket`, ISIN (`isin`, `Ticker("US0378331005")`) | not covered |

Three defaults differ from Python: history is **not** auto-adjusted (call `adjusted()`), a symbol
Yahoo does not know is an exception on `Ticker` and a `Skipped` outcome in a batch (never an empty
frame), and percents are stored as fractions.

## The model

### Hierarchy and tiers

```
Instrument            core(), assetClass(), symbol(), fetchedAt()
├── Equity            IntradayTraded, Quoted
├── Etf               IntradayTraded, Quoted, Fund
├── MutualFund        Fund                     (no intraday session, no order book)
├── Index             IntradayTraded, Quoted
├── Crypto            IntradayTraded           (no order book)
├── FxPair            IntradayTraded, Quoted
├── Future            IntradayTraded, Quoted
└── Unclassified      the universal core only
```

All interfaces are sealed. `Core` (22 fields, all non-null except `Optional<String> longName`) is
the universal tier: symbol, names, `QuoteCurrency`, exchange and time zone, `MarketState`, price,
change, previous close, 52-week range, moving averages, average volumes, first trade date. The tier
interfaces add what a group shares: `IntradayTraded.session()` (open, day low/high, volume),
`Quoted.book()` (an `Optional<TopOfBook>` — bid/ask, with `Optional` sizes), `Fund.ytdReturn()` /
`threeMonthReturn()`. Match on a tier when you only need that slice:

```java
if (instrument instanceof IntradayTraded traded) {
    long volume = traded.session().volume();
}
```

### Guarantees, `Optional`, and the downgrade

There is **no `@Nullable` anywhere in the public model.** A field is one of three things:

- **Absent from the class** — an `Index` has no `marketCap` accessor at all.
- **Guaranteed** — a plain non-null component. Guaranteed means *intrinsic to the class*: a field
  is non-null only if every miss in the 282-instrument survey was an instrument we accept
  downgrading (in practice the preferred share `BAC-PL`). A miss on an ordinary member — Samsung
  lacking `bookValue`, Costco lacking `beta` — makes the field `Optional` whatever its percentage,
  so you never lose Samsung's market cap because Yahoo omitted its book value.
- **`Optional<T>`** — Yahoo reports it for some members of the class, or only at some times of day
  (`postMarket`). Fields whose absences always co-occur are grouped into a cluster record
  (`TopOfBook`, `TrailingDividend`, `Equity.CurrentDividend`, `PostMarket`, `EquityLikeStats`,
  `EquityDetail.Targets`, …): a cluster is `Optional.of(cluster)` only when **every** member is
  present, so inside it nothing is optional again.

When a guaranteed field is missing anyway — Yahoo drifted, or the instrument is an odd one — the
snapshot **downgrades to `Unclassified`** instead of returning a null or throwing. `Unclassified`
still carries the full `Core`, plus `reportedQuoteType()`, `attempted()` (the class it failed to be)
and `missing()` (the field names). `instruments(symbols, Equity.class)` reports such a symbol as
`Skipped(DOWNGRADED)`; `as(Equity.class)` throws `YFClassMismatchException`. Downgrades log once at
`DEBUG` with the missing list. Lists (holders, holdings, trends, filings, officers) are never
`Optional`: empty when Yahoo omits the module.

### Two depths, and what they cost

| Depth | Types | Requests |
|---|---|---|
| **Snapshot** | the `Instrument` hierarchy | one `/v7/finance/quote` request per 100 distinct symbols (a chunk that fails marks only its own symbols `Failed`; the others proceed), plus at most one `quoteSummary` request per symbol whose v7 row left a guaranteed field short (the modules requested depend on the class); those fallbacks run `fanOutConcurrency()` at a time and the batch's INFO line reports `fallbacks=N` |
| **Detail** | `EquityDetail`, `EtfDetail`, `MutualFundDetail`, `CryptoDetail` | one `quoteSummary` request per instrument; `equityDetails(...)` and friends keep at most `EndpointConfig.fanOutConcurrency()` requests in flight (4 by default — raise it with `withFanOutConcurrency(n)`); `tickers(...).withConcurrency(n)` overrides the bound for one `fetch`/`histories` call |

Each snapshot field is assembled from both endpoints in a fixed precedence (v7 first, then the
quoteSummary modules) before it counts as missing, which is what lifts e.g. ETF trailing returns
from ~82 % to 100 %. `Index`, `FxPair` and `Future` have no detail tier: Yahoo has nothing beyond
price data for them. Detail records are fetched with the instrument as proof, so
`yf.ticker("SPY").detail(etf)` only compiles for an `Etf`.

### Units and values

- Percentages that Yahoo serves as percents (v7 `changePercent`, `postMarketChangePercent`,
  `dividendYield`, fund `expenseRatio`/`ytdReturn`/`threeMonthReturn`; `debtToEquity`,
  `fiveYearAvgDividendYield`, option `changePercent`) are **stored as fractions** (`0.0098`, not
  `0.98`), so every yield, margin, return and held-percent in the model is a fraction. The
  `quoteSummary` counterparts of the v7 percents already arrive as fractions, and the unit
  conversion is applied per source, so a value is the same fraction whichever endpoint supplied it.
- Epoch seconds and milliseconds become `Instant`; date-only epochs (fiscal year end, ex-dividend
  date, fund inception) become `LocalDate` (UTC). Corporate-action dates in a `PriceHistory` are
  best read as exchange-local dates: `dividend.localDate(history.zoneId())`.
- Currencies are `QuoteCurrency(code, Optional<Currency> iso)`. Pence-quoted instruments (`GBp` on
  the LSE, also `ZAc`, `ILA`) keep their code with an empty `iso()` and `isPence()` true; prices are
  in that unit, exactly as Yahoo reports them. Crypto `toCurrency` arrives as an FX ticker
  (`USD=X`) and is normalised to `USD`.
- Bars carry Yahoo's **raw** OHLC (non-null; a bar missing any of the four is dropped) plus
  `Optional` `adjClose` and `volume` (never coerced to 0). `PriceBar.adjusted()` /
  `PriceHistory.adjusted()` give Python yfinance's `auto_adjust=True` view by scaling OHLC by
  `adjClose / close`; nothing is adjusted silently. 30m history is fetched as 15m and resampled
  (Yahoo has returned 60m bars for 30m requests; Python yfinance applies the same workaround).

### Per-class notes

- **ETF** — `expenseRatio`, `netAssets` and `yield` are `Optional`: in the survey about one ETF
  in ten lacked the expense ratio in every source (which listings varies over time — CSPX.L, once
  a documented miss, now reports it). `ytdReturn` and
  `threeMonthReturn` are guaranteed. Equity-like stats (book value, P/B, shares, financial
  currency) come as one `Optional<EquityLikeStats>` cluster.
- **Mutual fund** — no intraday session and no order book (Yahoo prices funds once a day), so
  `MutualFund` is not `IntradayTraded`/`Quoted`. Net assets, expense ratio, yield, dividend rate
  and the two returns are guaranteed. The guarantees rest on US funds; non-US funds may downgrade
  until surveyed.
- **Options** are a discovered capability, never a class promise: `options()` is `Optional` and
  empty when the instrument has no listed expirations (`SAP.DE` and `^GSPC` have none, `^SPX` has
  hundreds). Within a chain, `bid`, `openInterest` and `volume` are `Optional`; a contract missing
  any other field is dropped.
- **Financial statements** exist for equities only (the timeseries endpoint returns empty series
  for every other class), hence the `Equity` proof. `FinancialStatement.value(item, period)`,
  `latest(item)` (at `latestPeriod()`, the last of the ascending `periods()`) and `row(item)`
  (period → value, ascending, absent values omitted) return `Optional<BigDecimal>` / an immutable
  map; the typed forms throw `IllegalArgumentException` for a `LineItem` of another statement
  (`TOTAL_ASSETS` asked of an income statement); the raw-key `value(String, period)` stays lenient. `statements(equity,
  Set<StatementType>, Set<Frequency>)` fetches every requested pair in **one** request and returns
  `type → frequency → FinancialStatement`; the trailing balance sheet (which Yahoo does not
  publish) is skipped when other pairs remain and an `IllegalArgumentException` when it is the
  only one, as it is for the single form.
- **`HistoryMetadata`** is fully non-null except `dataGranularity` (`Optional<Interval>`, in case
  Yahoo reports an interval this version does not know); an incomplete chart response throws
  rather than returning a half-filled record.

The normative list of every field, its kind (required / optional / cluster / list), its wire
sources in precedence order and the survey coverage behind it is
[Appendix A](docs/superpowers/specs/2026-09-26-typed-instrument-model-appendix.md) of the
[design](docs/superpowers/specs/2026-09-26-typed-instrument-model-design.md); a unit test keeps the
code's field tables identical to it. A weekly live test (`GuaranteeDriftTest`) re-classifies the
282-symbol survey and fails, naming the field, if Yahoo stops sending a guaranteed one — the
library itself degrades to `Unclassified` rather than breaking.

## What's covered

Per asset class — what the snapshot guarantees beyond the universal `Core`, what is `Optional`,
and which deeper calls exist. History and `options()` exist for every class (options may be empty).

| Class | Guaranteed (snapshot) | `Optional` (snapshot) | Detail | Statements |
|---|---|---|---|---|
| `Equity` | `session`, `valuation` (market cap, shares, implied shares, financial currency), `nextEarnings` | `book`, `bookValue`, `priceToBook`, `trailingEps`, `forwardEps`, `forwardPE`, `trailingPE`, `trailingDividend`, `currentDividend`, `currentYearEps`, `averageAnalystRating`, `postMarket` | `EquityDetail` | yes |
| `Etf` | `session`, `ytdReturn`, `threeMonthReturn` | `book`, `netAssets`, `expenseRatio`, `yield`, `navPrice`, `beta3Year`, `trailingThreeMonthNavReturns`, `trailingPE`, `equityLikeStats`, `trailingDividend`, `postMarket` | `EtfDetail` | — |
| `MutualFund` | `netAssets`, `expenseRatio`, `yield`, `dividendRate`, `ytdReturn`, `threeMonthReturn` | `equityLikeStats`, `trailingPE`, `trailingDividend` | `MutualFundDetail` | — |
| `Index` | `session` | `book` | — | — |
| `Crypto` | `session`, `marketCap`, `supply`, `volume24Hr`, `volumeAllCurrencies`, `fromCurrency`, `toCurrency`, `startDate`, `lastMarket`, `branding` | — | `CryptoDetail` | — |
| `FxPair` | `session` | `book` | — | — |
| `Future` | `session`, `contract` (expiry, open interest, underlying, continuous root) | `book` | — | — |

Detail contents: `EquityDetail` = `CompanyProfile`, `Statistics`, `FinancialHealth`, `AnalystView`
(recommendation, targets, trends, estimates, upgrades/downgrades, SEC filings), `Ownership`
(breakdown, institutions, funds, insiders, transactions, net purchase activity). `EtfDetail` /
`MutualFundDetail` share `FundDetail`: family, inception, trailing returns, annual returns,
allocation, equity valuation, holdings, sector weightings, bond ratings, category; the mutual-fund
record adds Morningstar ratings, turnover, minimums, load-adjusted returns and category ranks.
`CryptoDetail` = name, description, website, start date, fully diluted value, optional whitepaper,
Twitter and proof-of-work stats.

| Area | Endpoint | API |
|---|---|---|
| Snapshot, every asset class | `/v7/finance/quote` + `/v10/finance/quoteSummary` fallback | `Ticker.instrument()`, `as(...)`, `YFinance.instruments(...)` |
| Detail per class | `/v10/finance/quoteSummary` | `Ticker.detail(...)`, `YFinance.equityDetails(...)`, `etfDetails`, `mutualFundDetails`, `cryptoDetails` |
| Price history, dividends, splits, capital gains, metadata | `/v8/finance/chart` | `Ticker.history(...)`, `dividends()`, `splits()`, `YFinance.histories(...)` |
| Income / balance sheet / cash flow (annual, quarterly, trailing) | `/ws/fundamentals-timeseries` | `Ticker.statements(...)`, `YFinance.statements(...)` (single, several-in-one-request, and batch forms) |
| Options chain | `/v7/finance/options` | `Ticker.options(...)`, `YFinance.options(...)` |
| Search & per-symbol news | `/v1/finance/search` | `YFinance.search(...)`, `Ticker.news()` |
| Lookup | `/v1/finance/lookup` | `YFinance.lookup(...)` |

Not covered: live WebSocket streaming, `EquityQuery`/`Screener`, `Sector`/`Industry`.

## Configuration

Everything is tuned through `EndpointConfig` (an immutable record with `with...` copies):

```java
var config = EndpointConfig.production()
        .withCallTimeout(Duration.ofSeconds(10))    // bounds the whole call, paced waits and retries included
        .withFanOutConcurrency(8)                   // detail batches and the Tickers default; 4 if unset
        .withAdaptiveRateLimit(AdaptiveRateLimitConfig.defaults()
                .withMaxDelay(Duration.ofSeconds(5))   // pace cap (10 s if unset; clamped to callTimeout when longer)
                .withMaxAttempts(3))                   // attempts per request (1 = never retry a 429)
        .withTransientRetry(RetryConfig.defaults().withMaxAttempts(5))   // 5xx retries; 3 if unset
        .withCookieJar(myPersistentCookieJar)       // keep Yahoo's session cookies across restarts; in-memory if unset
        .withClock(Clock.systemUTC());              // the services' "now" (fetchedAt, period2); fix it in as-of tests

try (var yf = YFinance.create(config)) {
    // ...
}
```

Derive variants from `production()` with `withHosts(...)`, `withUserAgent(...)`, `withCallTimeout(...)`,
`withAdaptiveRateLimit(...)`, `withTransientRetry(...)`, `withFanOutConcurrency(...)`,
`withClientCustomizer(...)`, `withCookieJar(...)` and `withClock(...)`.
`AdaptiveRateLimitConfig.defaults()` is what `EndpointConfig.production()` uses (on, 500 ms → 10 s,
×2 per 429, ×0.5 per success, ±20 % jitter, 3 attempts); each field has a wither
(`withEnabled`, `withInitialDelay`, `withMaxDelay`, `withBackoffMultiplier`, `withRecoveryFactor`,
`withJitterFactor`, `withMaxAttempts`), as do `RetryConfig`'s three (`withMaxAttempts`,
`withInitialDelay`, `withMaxDelay`); validation runs on every copy.
`AdaptiveRateLimitConfig.disabled()` turns throttling and 429-retries off entirely.
A cookie jar you supply must be thread-safe (both clients write to it), and every `YFinance`
created from the same `EndpointConfig` instance shares it; `production()` gives each config a
fresh `InMemoryCookieJar`, which is also why two `production()` configs are never `equals`.
`callTimeout` (30 s by default) bounds the **whole** call, rate-limit pacing and retry backoffs
included. A `maxDelay` (10 s by default) longer than the call timeout could never be waited out,
so `YFinance.create` clamps it to the call timeout and logs one `WARN`
(`rate-limit maxDelay PT10S clamped to callTimeout PT5S`); set `maxDelay` explicitly, as above, to
silence it. Withers can be applied in any order.

To customise the underlying OkHttp clients (proxy, extra interceptors, metrics, connection pool),
supply a customizer; it runs last, after the library's own interceptors and timeouts. The two
clients (handshake and data) share one dispatcher and one connection pool; `close()` shuts those
down, but a dispatcher or pool the customizer installs is left running, since it is typically
shared with your other clients and is yours to close. (The dispatcher's `maxRequestsPerHost` is set
to `fanOutConcurrency` for calls you `enqueue` through that client; the library's own calls are
synchronous, and only the fan-out semaphore bounds them.)

```java
var config = EndpointConfig.production()
        .withClientCustomizer(b -> b
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.corp", 8080)))
                .addInterceptor(myMetricsInterceptor));
```

### Rate limiting and retries

`YFinance` is thread-safe — hold one instance (e.g. a singleton) and `close()` it on shutdown.
The shared client adaptively throttles on HTTP 429: a throttled request is retried up to
`maxAttempts` times (waiting the adapted, jittered delay, honoring `Retry-After`), and while
degraded **every** request is paced by the current delay (capped at `maxDelay`, 10 s by default)
until traffic recovers — no burst-429 oscillation. A burst of concurrent 429s for requests that were
already in flight raises the pace once, not once per response. Every paced wait stays inside the
call's `callTimeout`: a wait that could not fit in what is left of the budget fails immediately with
`YFRateLimitException("Rate-limit pacing of N ms exceeds the remaining call timeout for <path>")`,
whose `retryAfter()` is that wait, instead of sleeping and then timing out unsent. Otherwise only
after retries are exhausted is `YFRateLimitException` (with Yahoo's `retryAfter()`) thrown. A stale
crumb (401/403) is automatically invalidated and the request retried once; when several requests are
rejected at the same time, the first one's handshake refreshes the crumb and the others reuse it,
and a request that went out without a crumb is not retried while none is available. The handshake
itself is paced by the same limiter — a cold handshake inside an already-paced request rides in that
request's slot — and any failed handshake starts a cooldown of 30 s, doubling per consecutive failure
up to 5 min, or `Retry-After` when longer, during which no handshake is attempted (one `WARN` on
entering the cooldown, `DEBUG` after): a 429 or unreachable crumb endpoint means the client continues
without a crumb; a rejected handshake (403, blank or HTML crumb) throws `YFAuthException` once and
then stays quiet for the cooldown. Transient server errors (HTTP 500/502/503/504 — Yahoo's lookup
endpoint is known to hiccup) are retried with exponential backoff, honouring `Retry-After` and the
same call budget: 3 attempts by default, tunable or disabled via
`EndpointConfig.withTransientRetry(RetryConfig)`.

### Nullability

The public API is annotated with [JSpecify](https://jspecify.dev): every package is `@NullMarked`,
so an unannotated type is never null, and the model has no `@Nullable` at all — absence is an
`Optional` or a downgrade, as described above. IDEs, Kotlin and NullAway pick this up automatically;
the annotations are verified by NullAway on every build.

### Logging

The library logs through **SLF4J** (`slf4j-api` is its only logging dependency); bind whichever
backend your application uses (Logback, Log4j 2, `slf4j-simple`, …). Loggers are named after the
classes under `io.github.dimazigel.yfinance`. A healthy production log from this library is
**empty at `WARN`**:

| level | when | examples |
|---|---|---|
| `WARN` | degraded, or gave up | cookie/crumb unavailable (once, on entering the cooldown); still 429 or 5xx after all retries |
| `INFO` | once per client, once per multi-symbol batch (a single `Ticker` lookup logs its summary at `DEBUG`) | effective config at `create()`; rate limiter entering/leaving degraded mode; `instruments: 500 symbols: 497 ok, 2 skipped, 1 failed; fallbacks=12`; `Fetched 20 symbols: 20 ok, 0 skipped, 0 failed in 1 812 ms` |
| `DEBUG` | once per request or per dropped datum | `GET /v8/finance/chart/AAPL?range=1mo&interval=1d -> 200 (23 KB) in 412 ms`; `BAC-PL downgraded from EQUITY: missing [marketCap, impliedSharesOutstanding]`; `Dropped 4 of 390 bars without a complete OHLC`; each retry; each failed symbol in a batch |

The library never logs an error it also throws: the exception message carries Yahoo's reason and
the request path, and the caller decides what to do with it.

Every call runs inside an MDC scope so log lines can be correlated without parsing messages:
`yf.op` (`instruments`, `details`, `history`, `statements`, `options`, `search`, `lookup`,
`fetch`), `yf.symbol` (for a batch the first five symbols, then `,…+N`; the per-symbol scope is
opened on the worker thread that serves that symbol) and, while an HTTP request is in flight,
`yf.endpoint` (e.g. `/v8/finance/chart/AAPL`). Fan-out workers start with a copy of the calling
thread's MDC, so your own `traceId`/`requestId` appears on the per-symbol lines too. Event-specific
facts such as `status`, `attempt`, `delayMs`, `fallbacks` and `missing` are attached as SLF4J
key-value pairs. (Note: `slf4j-simple` has a no-op MDC, so use
Logback, Log4j 2 or another full backend to see them.) A Logback pattern that shows them:

```
%d %-5level [%X{yf.op}] %X{yf.symbol} %X{yf.endpoint} %logger{0} - %msg %kvp%n
```

## Error handling

All failures surface as `YFinanceException` subtypes (unchecked):

| Exception | Meaning | `isRetryable()` |
|---|---|---|
| `YFDataException` | Yahoo error envelope, malformed or incomplete response, or I/O failure | only when the cause is an `IOException` |
| ↳ `YFHttpException` | unexpected HTTP status; carries `status()` and `path()`, body in the message | `status() >= 500` |
| ↳ `YFMissingDataException` | `Ticker` asked for something Yahoo has nothing for: unknown symbol, or a detail whose guaranteed module is absent; `field()` and `subject()` | no |
| ↳↳ `YFSkippedException` | what `Outcome.Skipped.orElseThrow()` (and so every `Ticker` non-answer) actually throws; adds `reason()` (`SkipReason`) and `symbol()` | no |
| ↳ `YFClassMismatchException` | `as(Equity.class)` on an instrument of another class; `actual()` and `requested()` | no |
| `YFRateLimitException` | HTTP 429 after all adaptive retries (`retryAfter()` when Yahoo sent it), or a paced wait that cannot fit the call timeout (`retryAfter()` is that wait) | yes |
| `YFAuthException` | the cookie/crumb handshake failed, or Yahoo answered an HTML page instead of JSON (EU consent redirect or access blocked; the message names the path) | no |

`YFinanceException.isRetryable()` says whether repeating the same call later may succeed;
`Outcome.Failed.isRetryable()` delegates to it. Batch calls never throw per symbol: a failure
becomes `Outcome.Failed` and a non-answer becomes `Outcome.Skipped` (never retryable). Passing an instrument of a different symbol as
proof to `Ticker.detail(...)`/`statements(...)` is a programming error and throws
`IllegalArgumentException`.

## Architecture

```
YFinance / Ticker / Tickers — the facade; batch calls return Batch<T> (one Outcome<T> per symbol)
service/     one service per concern (InstrumentService, DetailService, HistoryService, ...)   [internal, except HistoryRequest, deprecated for removal in 2.0]
http/        client factory, interceptors (UA, crumb, auth-retry, adaptive rate limit),
             RawQuoteClient (batched v7 rows + per-symbol quoteSummary modules), YahooJsonMapper
api/         Feign interfaces (one per endpoint) + YahooApis bundle                            [internal]
assembly/    FieldSpec tables per class (specs/, mirrored from Appendix A), Resolver, builders (build/)   [internal]
instrument/  the sealed snapshot hierarchy and its value records
detail/      EquityDetail, EtfDetail, MutualFundDetail, CryptoDetail (+ rows/)
batch/       Batch, Outcome, SkipReason, FanOut
market/      HistoryQuery, PriceHistory, PriceBar, HistoryMetadata, OptionChain, corporate actions
fundamentals/ FinancialStatement;  search/ SearchResult, LookupQuote
dto/ + mapper/ raw records and mappers for chart, options, timeseries, search, lookup          [internal]
auth/        CrumbStore — cookie (fc.yahoo.com) then crumb handshake, invalidate-on-401/403 (by identity), cooldown after a transient failure   [internal]
enums/       closed sets implementing WireEnum (Interval, Range, LineItem, ...)
valueobject/ Symbol, Crumb
```

The packages marked *internal* are `public` only because the layers live in separate packages;
their `package-info` says so, they are left out of the published Javadoc, and they may change in
any release. The API is the facade, `instrument`, `detail`, `market`, `fundamentals`, `search`,
`batch` (not `FanOut`), `enums`, `valueobject`, `exception`, `logging`, the `http` configuration
records (`EndpointConfig`, `AdaptiveRateLimitConfig`, `RetryConfig`) plus `InMemoryCookieJar`,
and `service.HistoryRequest` (deprecated since 1.2, for removal in 2.0 — use
`market.HistoryQuery` instead); the interceptors, client factory, Feign/Jackson glue and
`RawQuoteClient` in `http` are internal too.

## Building, testing, consuming

```bash
./gradlew test                # fast, deterministic unit tests (MockWebServer + captured JSON) + JaCoCo
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

Unit tests never touch the network; they replay captured Yahoo responses from
`src/test/resources/fixtures/` (one snapshot per asset class plus edge cases such as a UCITS ETF,
a preferred share and a dead symbol). The live suite (`src/integrationTest`) verifies shape against
real responses and includes the guarantee-drift detector; it is excluded from `build` and runs
weekly in CI so that Yahoo API drift shows up as a failed run.

CI (GitHub Actions, `.github/workflows/build.yml`) runs `./gradlew build` on every push/PR and
uploads the JaCoCo coverage report as an artifact; CodeQL scans on every push, PR and weekly. The
build compiles main code with Error Prone, NullAway and `-Werror`, so a nullness mistake or an
Error Prone finding is a compile error. The Gradle configuration cache is enabled via
`gradle.properties`.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

> Note: Yahoo Finance has no public/supported API. This library mirrors what the
> Python `yfinance` project does and is for personal/research use; endpoints and
> response shapes can change without notice.
