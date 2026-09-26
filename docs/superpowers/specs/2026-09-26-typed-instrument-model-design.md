# Typed instrument model — design

Date: 2026-09-26 · Status: approved in conversation, awaiting written review · Scope: replaces the `model` package entirely

Companion files: [Appendix A — field tables](2026-09-26-typed-instrument-model-appendix.md) · [Evidence — Yahoo field survey](2026-09-26-yahoo-field-survey.md)

## 1. Problem and goal

The current model serves every asset class with one flat `Quote`/`Info` record, so 79 % of its 230 record components are `@Nullable` and the caller guesses which apply. The goal is a model where the **type system states what Yahoo guarantees for each kind of instrument**: holding an `Equity` gives a non-null `marketCap`; an `Index` simply has no such field. Breaking changes are acceptable (no consumers yet). Scope is every surface the library exposes.

## 2. Decisions taken (and why)

| # | Decision | Rationale |
|---|---|---|
| D1 | Guaranteed fields are **assembled from both endpoints** field by field before being considered missing. | Owner's choice; measured: the second source lifts ETF trailing returns, family, legal type and inception from ~82 % to 100 %. |
| D2 | A missing guaranteed field **downgrades the instrument** to `Unclassified` — never a null in a non-null slot, never an exception for that reason. | Owner's choice; keeps batch pipelines running and makes the failure diagnosable (`missing` list). |
| D3 | Scope is **everything**: snapshot, detail, history, options, statements, fund data. | Owner's choice. |
| D4 | Design base is the **sealed hierarchy** (Design A) with tier interfaces and `Optional` clusters, plus `Batch`/`Outcome` from Design D, the lite-inside-downgrade idea from Design B, and the per-field assembly table from Design C. | Four independent designs converged on the hierarchy, the tiers, the clusters and two fetch depths; they diverged on how depth is expressed — plain types beat a depth type parameter (`Slot`/`Full.get` wart) and beat plan/key laziness (runtime-checked `get`). |
| D5 | **Guarantee rule: Intrinsic.** A field is non-null for a class only if every miss in the wide survey is an instrument we accept downgrading (in practice the preferred share `BAC-PL`). A miss on a normal member (Samsung lacking `bookValue`, Costco lacking `beta`, TotalEnergies-Paris lacking bid/ask) makes the field `Optional`, whatever its percentage. | Owner's choice among Intrinsic / ≥ 99 % hard / Strict 100 %. Prevents losing Samsung's market cap because Yahoo omitted its book value. |
| D6 | **No `@Nullable` in the public model.** Absence is one of: field doesn't exist on the class; non-null with downgrade; `Optional<T>` (clustered where absences co-occur). | `Optional` makes optionality structural instead of tooling-dependent — the thing that produced 79 % nullable in the first place. |
| D7 | Options are a **discovered capability**, never a class promise. | Measured: `SAP.DE` (equity) has none, `^SPX` (index) has 170 calls, `^GSPC` none. |
| D8 | Financial statements are **equities-only** and take an `Equity` proof token. | Measured: the timeseries endpoint returns empty series for every other class. |
| D9 | Time-of-day fields (`postMarket*`) are always `Optional`. | 72 %/68 % presence depends on when you ask, not on the class. |
| D10 | Fund metrics on ETFs are **individual optionals**, not a cluster; the ETF expense ratio is `Optional`. | Measured: 18 of 38 ETFs have some but not all; four UCITS listings lack the expense ratio in every source. It *is* guaranteed for (US) mutual funds. |

## 3. Evidence

Three surveys through the library's own paced, authenticated client (never raw `curl`): 47 instruments across 7 classes for both endpoints; options/timeseries/chart metadata for 14 instruments; then a **wide survey of 282 live instruments** (173 equities across caps, ADRs, 15 non-US exchanges, preferreds and IPOs; 38 ETFs incl. UCITS; 15 US mutual funds; 18 indices; 12 crypto; 12 FX; 14 futures). Findings that changed the design: quoteSummary answers 200 for every class (the earlier 404s were narrower than "indices unsupported"); module presence per class is binary; mutual funds have no intraday session; several "100 %" fields from the narrow sample fell to 82–90 % at scale (`averageAnalystRating`, `trailingPE`, ETF fund metrics); the `Optional` clusters were verified by co-occurrence (six perfect, three needed splitting, one dissolved). Full numbers: evidence file; per-field consequences: Appendix A.

Known limits of the evidence: mutual funds are US-only (Yahoo's coverage is US-centric); options and `OptionContract` per-row keys were not surveyed at field level — to be measured during implementation before that record is frozen; a single day's snapshot, hence D9.

## 4. Architecture

### 4.1 Two depths, two type families
- **Snapshot** — what the batchable `/v7/finance/quote` carries, assembled with per-field fallback to `quoteSummary` (`price`, `summaryDetail`, `quoteType`) *only for symbols v7 left short*. 500 symbols ≈ 5 requests. This is the sealed hierarchy.
- **Detail** — the per-symbol `quoteSummary` data, typed per class: `EquityDetail`, `EtfDetail`, `MutualFundDetail`, `CryptoDetail`. `Index`, `FxPair`, `Future` have no detail tier (nothing beyond price data exists for them). Reached by overload so the compiler enforces which classes have which.

### 4.2 The hierarchy
```java
sealed interface Instrument permits Equity, Etf, MutualFund, Index, Crypto, FxPair, Future, Unclassified {
    Core core();          // universal tier, 22 fields, all non-null; Optional<String> longName
    AssetClass assetClass();
    default Symbol symbol() { return core().symbol(); }
}
sealed interface IntradayTraded permits Equity, Etf, Index, Crypto, FxPair, Future { Session session(); }
sealed interface Quoted         permits Equity, Etf, Index, FxPair, Future        { Optional<TopOfBook> book(); }
sealed interface Fund           permits Etf, MutualFund                             { /* detail-tier marker + shared snapshot fields */ }

record Equity(Core core, Session session, Optional<TopOfBook> book, Valuation valuation, NextEarnings nextEarnings,
              Optional<BookValueStats> bookStats, Optional<Eps> eps, Optional<BigDecimal> trailingPE,
              Optional<TrailingDividend> trailingDividend, Optional<CurrentDividend> currentDividend,
              Optional<CurrentYearEps> currentYearEps, Optional<String> averageAnalystRating,
              Optional<PostMarket> postMarket, Instant fetchedAt) implements Instrument, IntradayTraded, Quoted {}
record Valuation(BigDecimal marketCap, long sharesOutstanding, long impliedSharesOutstanding, Currency financialCurrency) {}
record Unclassified(Core core, String reportedQuoteType, Optional<AssetClass> attempted, List<String> missing,
                    Optional<Instrument> snapshot, Instant fetchedAt) implements Instrument {}
```
The remaining records follow the same pattern; their exact components are Appendix A, which is normative. Nested value records (`Core`, `Session`, `TopOfBook`, `Valuation`, `NextEarnings`, `Supply`, `Contract`, `FundMetrics`, …) are plain records with all-non-null components — a cluster is `Optional.of(cluster)` only when every member is present.

### 4.3 Detail records
`EquityDetail(CompanyProfile profile, Statistics statistics, FinancialHealth financials, AnalystView analysts, Ownership ownership, Instant fetchedAt)`; `EtfDetail`/`MutualFundDetail` share `FundProfile`, `TrailingReturns`, `Allocation`, `EquityValuation`, holdings lists; `CryptoDetail(name, description, website, startDate, fullyDilutedValue, Optional whitepaper, Optional twitter, Optional<ProofOfWork>)`. Lists (holders, holdings, trends, filings) are never `Optional`: empty when Yahoo omits the module.

### 4.4 Beyond quotes
- `market.PriceBar(timestamp, open, high, low, close, Optional<BigDecimal> adjClose, Optional<Long> volume)` — OHLC were present in every sampled bar with a close; the existing "drop bars without a close" rule extends to any null OHLC. `HistoryMetadata`: `currency`, `exchange`, `timezone`, `instrumentType`, `regularMarketPrice`, `dataGranularity`, `validRanges`, `currentTradingPeriod` non-null (14/14 charts); `tradingPeriods` `Optional` (mutual funds lack it).
- `market.OptionChain`: `contractSymbol`, `strike`, `expiration`, `type` non-null; market fields `Optional` pending the per-row survey (§3).
- `fundamentals.FinancialStatement`: shape unchanged; obtained only through an `Equity`.

### 4.5 Packages
`instrument`, `detail`, `batch`, `market`, `fundamentals`; existing `valueobject`, `enums`, `exception`, `logging`, `http`, `auth`, `api`, `dto`, `service`, `mapper` stay. `model` is deleted.

## 5. API

```java
// YFinance — batch-first, N in → N out, never throws per symbol
Batch<Instrument>               instruments(Collection<Symbol> symbols);
<I extends Instrument> Batch<I> instruments(Collection<Symbol> symbols, Class<I> as);   // others → Skipped
Batch<EquityDetail>             details(Collection<Equity>);   Batch<EtfDetail> details(Collection<Etf>);
Batch<MutualFundDetail>         details(Collection<MutualFund>); Batch<CryptoDetail> details(Collection<Crypto>);
Batch<PriceHistory>             histories(Collection<Symbol>, HistoryRequest);
Batch<FinancialStatement>       statements(Collection<Equity>, StatementType, Frequency);
Batch<Optional<OptionChain>>    options(Collection<Symbol>);
SearchResult search(String); List<LookupQuote> lookup(String, LookupType);           // unchanged

// Ticker — single symbol; value or exception
Instrument instrument();  <I extends Instrument> I as(Class<I>);
EquityDetail detail(Equity); EtfDetail detail(Etf); MutualFundDetail detail(MutualFund); CryptoDetail detail(Crypto);
PriceHistory history(HistoryRequest | Range, Interval | start, end, Interval);   // as today
Optional<OptionChain> options();  Optional<OptionChain> options(Instant expiration);
FinancialStatement statements(Equity proof, StatementType, Frequency);
List<Dividend> dividends(); List<Split> splits(); List<NewsArticle> news();           // as today

// Tickers — generic fan-out, now yielding Batch
<T> Batch<T> fetch(Function<? super Ticker, T> fetcher);   Tickers withConcurrency(int);
```

`Batch<T>`: input-ordered `List<Outcome<T>>` with `values()`, `skipped()`, `failed()`, `summary()`. `Outcome<T>` = `Ok(symbol, value)` | `Skipped(symbol, SkipReason, detail)` | `Failed(symbol, YFinanceException)`. `SkipReason` = `UNKNOWN_SYMBOL`, `WRONG_ASSET_CLASS`, `DOWNGRADED`, `NOT_AVAILABLE_FOR_CLASS`, `MODULE_ABSENT`. `Skipped` is non-retryable, `Failed` retryable — the distinction today's `Result` cannot make. `Tickers.Result`, `Required` and the `require(...)` helpers are removed.

## 6. Data flow — the assembly table executes

1. **Classify** `quoteType` → class; anything outside the seven (`OPTION`, `ECNQUOTE`, `NONE`) → `Unclassified(reported)`. Symbols absent from the v7 result → `Skipped(UNKNOWN_SYMBOL)`.
2. **Resolve** every field of the class through its `FieldSpec(name, kind, wire paths in precedence order, unit)` from Appendix A. Precedence: `v7`, then `price`, `summaryDetail`, `quoteType`, `defaultKeyStatistics`, then class modules. Sources agree on values; precedence only decides absence.
3. **Fallback**: any REQUIRED snapshot field missing after v7 → one `quoteSummary?modules=price,summaryDetail,quoteType` request for that symbol, resolve again. Detail: one `quoteSummary` request per symbol with the class's module set.
4. **Validate and build**: all REQUIRED present → record; clusters become `Optional.of(...)` only if every member resolved. Any REQUIRED missing → snapshot: `Unclassified(core, reported, attempted, missing)`; detail: `Skipped(MODULE_ABSENT, "financialData.totalRevenue")`, instrument untouched.
5. **Normalise** units per Appendix A (percent→fraction, epoch s/ms → `Instant`, dates → `LocalDate`); `raw/fmt` objects already handled by `RawAwareNumberModule`.

The table is data (a Java enum or constant list per class), the mapper is one generic executor; adding a source to a field is a one-line change, and a test asserts the table matches the appendix.

## 7. Error handling

- Transport, 429 and 5xx after retries, malformed JSON → `Failed(YFinanceException)` in batches; thrown from `Ticker`.
- `Ticker.as(Equity.class)` on a non-equity → `YFClassMismatchException` (new; subtype of `YFDataException`; carries actual class). `Ticker.detail(...)` with a required module missing → `YFMissingDataException` (existing; `field`, `subject`).
- Downgrades never throw; each logs once at DEBUG with the missing list; `Batch.summary()` counts them (INFO once per batch, as today).
- Retry, rate-limit, auth, logging and MDC layers are unchanged.

## 8. Testing

- **Fixtures become real captures**: one trimmed survey response per class plus edge cases (UCITS ETF without expense ratio, preferred share, loss-making equity, `NONE` quoteType, mutual fund without session), replacing the hand-written JSON.
- **Unit tests per class**: classification; each REQUIRED field resolved from each source in turn; fallback issued only when needed (request count asserted); downgrade with the exact `missing` list; cluster all-or-nothing; unit normalisation; `Skipped` vs `Failed`; `Batch` order preservation.
- **Table conformance test**: the in-code `FieldSpec`s equal the appendix (generated from the same source, compared in a test).
- **By construction**: a REQUIRED field is a non-null record component; NullAway keeps it so.
- **Live drift detector**: a weekly test runs `instruments(...)` over the 282-symbol survey list and asserts ≥ 99 % of live instruments per class classify without downgrade, printing offenders. A dropped field turns the run red with the field name.
- Existing gates (coverage floor 85/60, Error Prone, Spotless) unchanged; coverage is expected to rise.

## 9. Migration and removal

Deleted: `model.*` (`Quote`, `Info`, `Holders`, `AnalystPriceTarget`, `PeriodEstimate`, …, `Required`), `Tickers.Result`, `QuoteService.getInfo` and its fallback (subsumed by assembly), `HoldersService`/`AnalysisService` as public surfaces (their modules fold into `EquityDetail`). README API sections, AGENTS.md conventions and the memory notes are rewritten. Releases: this is the breaking change that justifies the next minor version.

## 10. Open items and risks

- **OptionContract per-row keys** — unmeasured; measure during implementation (same survey method) before freezing that record.
- **Mutual funds** — guarantees rest on 15 US funds; non-US funds may downgrade until surveyed. Acceptable under D2, flagged in README.
- **`currency` for pence-quoted instruments** (`GBp`) — not an ISO code; today mapped leniently to null. Under D6 the core's `Currency` is non-null, so the options are a `QuoteCurrency` value type carrying the raw code and an optional ISO `Currency`, or downgrading `.L` instruments — the former is planned; to be confirmed in the implementation plan.
- **Expense-ratio units** — v7 `netExpenseRatio` and `fundProfile.feesExpensesInvestment.annualReportExpenseRatio` must be compared on a fixture to confirm both are percents (appendix says *verify at impl*).
- **Drift** — Yahoo can change presence at any time; the weekly live check (§8) is the control, and D2 means a change degrades gracefully to `Unclassified` rather than breaking callers.
