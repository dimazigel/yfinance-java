# Typed Instrument Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat, 79 %-nullable `model` package with a sealed per-asset-class instrument hierarchy whose non-null fields are exactly what Yahoo guarantees for that class, assembled from both Yahoo endpoints and downgraded (never nulled) when a guarantee fails.

**Architecture:** A data-driven assembler resolves per-class `FieldSpec` tables (the spec's Appendix A, normative) against raw JSON from `/v7/finance/quote` (batchable snapshot) and `/v10/finance/quoteSummary` (per-symbol detail); per-class builders turn a `Resolved` field map into records. Batch calls return `Batch<Outcome<T>>` distinguishing non-retryable `Skipped` from retryable `Failed`. The HTTP/auth/retry/logging layers are untouched.

**Tech Stack:** Java 21 (records, sealed types, pattern matching), Retrofit 3 + OkHttp 5, Jackson 2 (`JsonNode` for the two assembled endpoints, DTOs elsewhere), JSpecify + NullAway, JUnit 5 + AssertJ + MockWebServer, Gradle 9.

**Spec:** `docs/superpowers/specs/2026-09-26-typed-instrument-model-design.md` (design), `…-appendix.md` (normative field tables), `…-yahoo-field-survey.md` (evidence). Read the design and skim the appendix before any task.

## Global Constraints

- Java 21 toolchain; every package `@NullMarked`; **no `@Nullable` anywhere in the public model** (`instrument`, `detail`, `batch`, `market`, `fundamentals`): absence is "field doesn't exist", non-null-with-downgrade, or `Optional<T>` (design D6).
- Field names, kinds (R/O/C/L) and wire-path precedence come from Appendix A verbatim; the in-code tables must pass the conformance test (Task 18). Precedence: `v7`, then `price`, `summaryDetail`, `quoteType`, `defaultKeyStatistics`, then class modules (design §6).
- Guarantee rule is **Intrinsic** (design D5): required fields are exactly the `R` rows of the appendix — do not promote or demote a field without changing the appendix.
- Unit rules (appendix header): `changePercent`, v7 `dividendYield`, `fiveYearAvgDividendYield`, `debtToEquity`, `postMarketChangePercent` are percents on the wire → stored as fractions; all other yields/margins/held-percents are already fractions; epoch seconds/millis → `Instant`; date-only epochs → `LocalDate` (UTC).
- v7 requests are chunked at **100 symbols** (verified in the survey); the snapshot fallback fetches only `price,summaryDetail,quoteType` and only for symbols missing a required field (design §6.3).
- `Batch` preserves input order, N in → N out; duplicates in the input yield duplicate outcomes; empty input makes no request.
- Build gates stay: `./gradlew build` (Error Prone defaults + `-Werror` + NullAway, Spotless, JaCoCo floor 85 % line / 60 % branch). Run `./gradlew spotlessApply` before every commit.
- Logging: SLF4J fluent API only; downgrades log once at DEBUG with the missing list; per-batch INFO summary; `LogContext.scope(op, symbol)` around each new service entry (`op` = `instruments`, `details`, `options`, `statements`).
- Never probe Yahoo with raw `curl`; live captures go through `YahooClientFactory.apiClient(EndpointConfig.production())` in a `@Tag("live")` test.
- Commit after every task with the `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` trailer; branch protection requires a PR with a green `build` for `main`.

## Review Focus

Spec-implied conditions no single feature test would otherwise exercise; each is pinned in the task named:

1. **quoteSummary values arrive as `{raw, fmt}` objects even with `formatted=false`** (the library's oldest gotcha): the resolver must unwrap `raw` before conversion, or every second-source fallback silently fails. → Task 3, `resolvesRawFmtObjects`.
2. **Yahoo normalises symbols differently from the request** (`brk-b` → `BRK-B`; aliases): a v7 row must be matched back to the *requested* `Symbol`, else every lower-case request becomes `UNKNOWN_SYMBOL`. → Task 9, `matchesRowsToRequestedSymbolsCaseInsensitively`.
3. **Duplicate and empty inputs**: `instruments(List.of(AAPL, AAPL))` must return two outcomes; `instruments(List.of())` must make zero requests. → Task 9, `duplicatesYieldDuplicateOutcomes`, `emptyInputMakesNoRequest`.
4. **Percent-vs-fraction across sources**: v7 `dividendYield` (`0.32`) and `summaryDetail.dividendYield` (`0.0032`) must produce the same stored fraction whichever source answered. → Task 6, `currentDividendYieldIsAFractionFromEitherSource`.
5. **A 404 "Quote not found" on quoteSummary for a live v7 symbol** (Yahoo does this for delisted-but-still-quoted names): the detail call must yield `Skipped(UNKNOWN_SYMBOL)`, not `Failed`, and must never abort a batch. → Task 12, `detailForVanishedSymbolIsSkippedNotFailed`.

---

## File structure

New packages under `src/main/java/io/github/dimazigel/yfinance/`:

| path | responsibility |
|---|---|
| `batch/SkipReason.java`, `batch/Outcome.java`, `batch/Batch.java` | batch result shape (design §5) |
| `instrument/AssetClass.java`, `MarketState.java`, `QuoteCurrency.java` | classification and value types |
| `instrument/Core.java`, `Session.java`, `TopOfBook.java`, `PostMarket.java` | universal and tier value records |
| `instrument/Instrument.java`, `IntradayTraded.java`, `Quoted.java`, `Fund.java` | sealed root and tier interfaces |
| `instrument/Equity.java` (+ `Valuation`, `NextEarnings`, `BookValueStats`, `TrailingDividend`, `CurrentDividend`, `CurrentYearEps` nested), `Etf.java` (+ `EquityLikeStats`), `MutualFund.java`, `Index.java`, `FxPair.java`, `Crypto.java` (+ `Supply`, `Branding`), `Future.java` (+ `Contract`), `Unclassified.java` | the hierarchy |
| `detail/EquityDetail.java` (+ `CompanyProfile`, `Statistics`, `FinancialHealth`, `AnalystView`, `Ownership` and their nested records), `detail/FundDetail.java` (shared: `FundProfile`, `TrailingReturns`, `Allocation`, `EquityValuation`, `Holding`, `SectorWeight`, `BondRating`, `YearReturn`), `detail/EtfDetail.java`, `detail/MutualFundDetail.java`, `detail/CryptoDetail.java` | detail tier |
| `assembly/Source.java`, `WirePath.java`, `Unit.java`, `Kind.java`, `FieldSpec.java`, `Payload.java`, `Resolved.java`, `Resolver.java` | the generic executor of the appendix tables |
| `assembly/specs/CoreSpecs.java`, `SessionSpecs.java`, `BookSpecs.java`, `PostMarketSpecs.java`, `EquitySpecs.java`, `EtfSpecs.java`, `MutualFundSpecs.java`, `CryptoSpecs.java`, `FutureSpecs.java`, `FundDetailSpecs.java`, `SnapshotSpecs.java` | the tables (data) |
| `assembly/build/CoreBuilder.java`, `EquityBuilder.java`, `EtfBuilder.java`, `MutualFundBuilder.java`, `SimpleBuilders.java` (Index/FxPair), `CryptoBuilder.java`, `FutureBuilder.java`, `EquityDetailBuilder.java`, `FundDetailBuilder.java`, `CryptoDetailBuilder.java` | `Resolved` → records |
| `api/QuoteApi.java`, `api/QuoteSummaryApi.java` (modified: return `JsonNode`), `http/RawQuoteClient.java`, `exception/YFHttpException.java`, `exception/YFClassMismatchException.java` | raw access and new exceptions |
| `service/InstrumentService.java`, `service/DetailService.java` | orchestration: classify → resolve → fallback → build/downgrade |
| `market/` (moved from `model`: `PriceHistory`, `PriceBar`, `HistoryMetadata`, `Dividend`, `Split`, `CapitalGain`, `OptionChain`, `OptionContract`, `PriceBarResampler` from `mapper`), `fundamentals/` (moved: `FinancialStatement`) | non-quote surfaces, re-typed |

Deleted at Task 16: `model/*`, `service/QuoteService`, `HoldersService`, `AnalysisService`, `mapper/QuoteSummaryMapper`, `QuoteMapper`, `HoldersMapper`, `AnalysisMapper`, `dto/quote/*`, `dto/quotesummary/*`, `Tickers.Result`.

Test resources: `src/test/resources/fixtures/instruments/{v7,qs}_<SYMBOL>.json` (real captures, Task 4), `src/integrationTest/resources/survey-symbols.txt` (Task 17).

Phases: **0** foundations (Tasks 1–4) · **1** snapshot hierarchy (5–9) · **2** detail tier (10–12) · **3** market & fundamentals (13–15) · **4** facade, removal, live suite, docs (16–19). The build is green after every task; old and new models coexist until Task 16.

---

## Phase 0 — Foundations

### Task 1: `batch` package — `Outcome`, `SkipReason`, `Batch`

**Files:**
- Create: `src/main/java/io/github/dimazigel/yfinance/batch/SkipReason.java`, `Outcome.java`, `Batch.java`, `package-info.java`
- Test: `src/test/java/io/github/dimazigel/yfinance/batch/BatchTest.java`

**Interfaces:**
- Consumes: `Symbol`, `YFinanceException`, `YFDataException` (existing).
- Produces: `Outcome<T>` sealed with `Ok(Symbol symbol, T value)`, `Skipped(Symbol symbol, SkipReason reason, String detail)`, `Failed(Symbol symbol, YFinanceException error)`; `Outcome.value(): Optional<T>`, `orElseThrow(): T`, static `ok/skipped/failed`. `Batch<T>(List<Outcome<T>> outcomes)` with `values()`, `skipped()`, `failed()`, `get(Symbol)`, `size()`, `summary()`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.dimazigel.yfinance.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchTest {

    private static final Symbol AAPL = Symbol.of("AAPL");
    private static final Symbol MSFT = Symbol.of("MSFT");
    private static final Symbol NOPE = Symbol.of("NOPE");

    @Test
    void outcomesPreserveOrderAndSplitByKind() {
        var batch = new Batch<>(List.of(
                Outcome.ok(AAPL, 1), Outcome.skipped(MSFT, SkipReason.WRONG_ASSET_CLASS, "ETF"),
                Outcome.failed(NOPE, new YFDataException("boom"))));

        assertThat(batch.size()).isEqualTo(3);
        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(AAPL, MSFT, NOPE);
        assertThat(batch.values()).containsExactly(1);
        assertThat(batch.skipped()).singleElement().satisfies(s -> {
            assertThat(s.reason()).isEqualTo(SkipReason.WRONG_ASSET_CLASS);
            assertThat(s.detail()).isEqualTo("ETF");
        });
        assertThat(batch.failed()).singleElement().satisfies(f -> assertThat(f.error()).hasMessage("boom"));
        assertThat(batch.get(MSFT)).containsInstanceOf(Outcome.Skipped.class);
        assertThat(batch.get(Symbol.of("X"))).isEmpty();
        assertThat(batch.summary()).isEqualTo("3 symbols: 1 ok, 1 skipped, 1 failed");
    }

    @Test
    void outcomeAccessors() {
        Outcome<Integer> ok = Outcome.ok(AAPL, 7);
        assertThat(ok.value()).contains(7);
        assertThat(ok.orElseThrow()).isEqualTo(7);

        Outcome<Integer> skipped = Outcome.skipped(MSFT, SkipReason.DOWNGRADED, "marketCap");
        assertThat(skipped.value()).isEmpty();
        assertThatThrownBy(skipped::orElseThrow).isInstanceOf(YFDataException.class)
                .hasMessage("MSFT skipped: DOWNGRADED (marketCap)");

        var boom = new YFDataException("boom");
        Outcome<Integer> failed = Outcome.failed(NOPE, boom);
        assertThatThrownBy(failed::orElseThrow).isSameAs(boom);
    }

    @Test
    void batchIsImmutableAndExhaustivelySwitchable() {
        var batch = new Batch<>(new java.util.ArrayList<>(List.of(Outcome.ok(AAPL, "v"))));
        assertThatThrownBy(() -> batch.outcomes().add(Outcome.ok(MSFT, "w")))
                .isInstanceOf(UnsupportedOperationException.class);
        String label = switch (batch.outcomes().getFirst()) {   // no default: sealed
            case Outcome.Ok<String> o -> "ok:" + o.value();
            case Outcome.Skipped<String> s -> "skipped";
            case Outcome.Failed<String> f -> "failed";
        };
        assertThat(label).isEqualTo("ok:v");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'io.github.dimazigel.yfinance.batch.BatchTest'`
Expected: compilation FAILS — `package io.github.dimazigel.yfinance.batch does not exist`.

- [ ] **Step 3: Write the implementation**

`package-info.java`:
```java
@NullMarked
package io.github.dimazigel.yfinance.batch;

import org.jspecify.annotations.NullMarked;
```

`SkipReason.java`:
```java
package io.github.dimazigel.yfinance.batch;

/** Why a symbol produced no value although nothing went wrong on the wire. Not worth retrying. */
public enum SkipReason {
    /** Yahoo does not know the symbol (absent from the quote response, or 404 on quoteSummary). */
    UNKNOWN_SYMBOL,
    /** The caller asked for one asset class and the symbol is another. */
    WRONG_ASSET_CLASS,
    /** A guaranteed field was missing after all sources; see {@code Unclassified.missing()}. */
    DOWNGRADED,
    /** The requested data set does not exist for this asset class (e.g. statements for an index). */
    NOT_AVAILABLE_FOR_CLASS,
    /** A quoteSummary module the class guarantees was absent for this symbol. */
    MODULE_ABSENT
}
```

`Outcome.java`:
```java
package io.github.dimazigel.yfinance.batch;

import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.Objects;
import java.util.Optional;

/**
 * The result for one symbol in a batch: a value, a non-retryable skip with a reason, or a
 * retryable failure. Sealed, so a {@code switch} over it needs no default.
 */
public sealed interface Outcome<T> permits Outcome.Ok, Outcome.Skipped, Outcome.Failed {

    Symbol symbol();

    /** The value on {@link Ok}, empty otherwise. */
    default Optional<T> value() {
        return this instanceof Ok<T> ok ? Optional.of(ok.value) : Optional.empty();
    }

    /** The value, or the failure rethrown, or a {@link YFDataException} describing the skip. */
    T orElseThrow();

    static <T> Outcome<T> ok(Symbol symbol, T value) {
        return new Ok<>(symbol, value);
    }

    static <T> Outcome<T> skipped(Symbol symbol, SkipReason reason, String detail) {
        return new Skipped<>(symbol, reason, detail);
    }

    static <T> Outcome<T> failed(Symbol symbol, YFinanceException error) {
        return new Failed<>(symbol, error);
    }

    record Ok<T>(Symbol symbol, T value) implements Outcome<T> {
        public Ok {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(value, "value");
        }

        @Override
        public T orElseThrow() {
            return value;
        }
    }

    record Skipped<T>(Symbol symbol, SkipReason reason, String detail) implements Outcome<T> {
        public Skipped {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }

        @Override
        public T orElseThrow() {
            throw new YFDataException(symbol + " skipped: " + reason + " (" + detail + ")");
        }
    }

    record Failed<T>(Symbol symbol, YFinanceException error) implements Outcome<T> {
        public Failed {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(error, "error");
        }

        @Override
        public T orElseThrow() {
            throw error;
        }
    }
}
```

`Batch.java`:
```java
package io.github.dimazigel.yfinance.batch;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.Optional;

/** N symbols in, N outcomes out, in input order. Never throws per symbol. */
public record Batch<T>(List<Outcome<T>> outcomes) {

    public Batch {
        outcomes = List.copyOf(outcomes);
    }

    public int size() {
        return outcomes.size();
    }

    /** Values of the {@link Outcome.Ok} outcomes, in order. */
    public List<T> values() {
        return outcomes.stream().flatMap(o -> o.value().stream()).toList();
    }

    public List<Outcome.Skipped<T>> skipped() {
        return outcomes.stream().filter(Outcome.Skipped.class::isInstance).map(o -> (Outcome.Skipped<T>) o).toList();
    }

    public List<Outcome.Failed<T>> failed() {
        return outcomes.stream().filter(Outcome.Failed.class::isInstance).map(o -> (Outcome.Failed<T>) o).toList();
    }

    /** The first outcome for {@code symbol}, if it was in the input. */
    public Optional<Outcome<T>> get(Symbol symbol) {
        return outcomes.stream().filter(o -> o.symbol().equals(symbol)).findFirst();
    }

    /** One line for logs: {@code "3 symbols: 1 ok, 1 skipped, 1 failed"}. */
    public String summary() {
        int skipped = skipped().size();
        int failed = failed().size();
        return size() + " symbols: " + (size() - skipped - failed) + " ok, " + skipped + " skipped, " + failed + " failed";
    }
}
```
If Error Prone flags the unchecked casts in `skipped()`/`failed()`, replace them with a pattern-matching loop into an `ArrayList` and `List.copyOf`.

- [ ] **Step 4: Run the test**

Run: `./gradlew spotlessApply test --tests 'io.github.dimazigel.yfinance.batch.BatchTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/dimazigel/yfinance/batch src/test/java/io/github/dimazigel/yfinance/batch
git commit -m "Add batch package: Outcome (Ok/Skipped/Failed), SkipReason, Batch" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `instrument` value types — `AssetClass`, `MarketState`, `QuoteCurrency`, `Core`, `Session`, `TopOfBook`, `PostMarket`

**Files:**
- Create: `src/main/java/io/github/dimazigel/yfinance/instrument/package-info.java`, `AssetClass.java`, `MarketState.java`, `QuoteCurrency.java`, `Core.java`, `Session.java`, `TopOfBook.java`, `PostMarket.java`
- Test: `src/test/java/io/github/dimazigel/yfinance/instrument/ValueTypesTest.java`

**Interfaces:**
- Produces: `AssetClass { EQUITY, ETF, MUTUAL_FUND, INDEX, CRYPTO, FX, FUTURE, UNCLASSIFIED; static Optional<AssetClass> fromQuoteType(String) }`; `MarketState { PRE, REGULAR, POST, CLOSED, PREPRE, POSTPOST, OTHER; static MarketState fromWire(String) }`; `record QuoteCurrency(String code, Optional<Currency> iso) { static QuoteCurrency of(String code) }`; `record Core(Symbol symbol, String shortName, Optional<String> longName, QuoteCurrency currency, String exchange, String fullExchangeName, ZoneId exchangeTimezone, MarketState marketState, BigDecimal price, BigDecimal change, BigDecimal changePercent, BigDecimal previousClose, Instant priceTime, BigDecimal fiftyTwoWeekLow, BigDecimal fiftyTwoWeekHigh, BigDecimal fiftyDayAverage, BigDecimal twoHundredDayAverage, long averageVolume10Day, long averageVolume3Month, Instant firstTradeDate, int priceHint, boolean hasPrePostMarketData)`; `record Session(BigDecimal open, BigDecimal dayLow, BigDecimal dayHigh, long volume)`; `record TopOfBook(BigDecimal bid, BigDecimal ask, Optional<Long> bidSize, Optional<Long> askSize)`; `record PostMarket(BigDecimal price, BigDecimal change, BigDecimal changePercent, Instant time)`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.dimazigel.yfinance.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValueTypesTest {

    @Test
    void assetClassMapsYahooQuoteTypes() {
        assertThat(AssetClass.fromQuoteType("EQUITY")).contains(AssetClass.EQUITY);
        assertThat(AssetClass.fromQuoteType("ETF")).contains(AssetClass.ETF);
        assertThat(AssetClass.fromQuoteType("MUTUALFUND")).contains(AssetClass.MUTUAL_FUND);
        assertThat(AssetClass.fromQuoteType("INDEX")).contains(AssetClass.INDEX);
        assertThat(AssetClass.fromQuoteType("CRYPTOCURRENCY")).contains(AssetClass.CRYPTO);
        assertThat(AssetClass.fromQuoteType("CURRENCY")).contains(AssetClass.FX);
        assertThat(AssetClass.fromQuoteType("FUTURE")).contains(AssetClass.FUTURE);
        assertThat(AssetClass.fromQuoteType("OPTION")).isEmpty();
        assertThat(AssetClass.fromQuoteType("NONE")).isEmpty();
        assertThat(AssetClass.fromQuoteType(null)).isEmpty();
    }

    @Test
    void marketStateIsLenient() {
        assertThat(MarketState.fromWire("REGULAR")).isEqualTo(MarketState.REGULAR);
        assertThat(MarketState.fromWire("PREPRE")).isEqualTo(MarketState.PREPRE);
        assertThat(MarketState.fromWire("something-new")).isEqualTo(MarketState.OTHER);
        assertThat(MarketState.fromWire(null)).isEqualTo(MarketState.OTHER);
    }

    @Test
    void quoteCurrencyKeepsNonIsoCodes() {
        assertThat(QuoteCurrency.of("USD")).isEqualTo(new QuoteCurrency("USD", Optional.of(Currency.getInstance("USD"))));
        assertThat(QuoteCurrency.of("GBp").iso()).isEmpty();      // pence: not an ISO code, but not lost either
        assertThat(QuoteCurrency.of("GBp").code()).isEqualTo("GBp");
        assertThat(QuoteCurrency.of("GBp").isPence()).isTrue();
        assertThat(QuoteCurrency.of("USD").isPence()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'io.github.dimazigel.yfinance.instrument.ValueTypesTest'`
Expected: compilation FAILS — package does not exist.

- [ ] **Step 3: Write the implementation**

`package-info.java`: `@NullMarked package io.github.dimazigel.yfinance.instrument;` with the import, as in Task 1.

`AssetClass.java`:
```java
package io.github.dimazigel.yfinance.instrument;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The asset classes the model distinguishes; maps from Yahoo's {@code quoteType}. */
public enum AssetClass {
    EQUITY, ETF, MUTUAL_FUND, INDEX, CRYPTO, FX, FUTURE, UNCLASSIFIED;

    /** The class for a Yahoo {@code quoteType}, empty for anything the model does not type. */
    public static Optional<AssetClass> fromQuoteType(@Nullable String quoteType) {
        if (quoteType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (quoteType) {
            case "EQUITY" -> EQUITY;
            case "ETF" -> ETF;
            case "MUTUALFUND" -> MUTUAL_FUND;
            case "INDEX" -> INDEX;
            case "CRYPTOCURRENCY" -> CRYPTO;
            case "CURRENCY" -> FX;
            case "FUTURE" -> FUTURE;
            default -> null;
        });
    }
}
```
(`@Nullable` on a *parameter* of a static helper is allowed; the rule forbids it on model record components and accessors.)

`MarketState.java`:
```java
package io.github.dimazigel.yfinance.instrument;

import org.jspecify.annotations.Nullable;

/** Yahoo's session state; unknown values map to {@link #OTHER} rather than failing. */
public enum MarketState {
    PRE, REGULAR, POST, CLOSED, PREPRE, POSTPOST, OTHER;

    public static MarketState fromWire(@Nullable String wire) {
        if (wire == null) {
            return OTHER;
        }
        return switch (wire) {
            case "PRE" -> PRE;
            case "REGULAR" -> REGULAR;
            case "POST" -> POST;
            case "CLOSED" -> CLOSED;
            case "PREPRE" -> PREPRE;
            case "POSTPOST" -> POSTPOST;
            default -> OTHER;
        };
    }
}
```

`QuoteCurrency.java`:
```java
package io.github.dimazigel.yfinance.instrument;

import java.util.Currency;
import java.util.Objects;
import java.util.Optional;

/**
 * The currency Yahoo quotes an instrument in. Usually an ISO code ({@code USD}), but London quotes
 * in pence ({@code GBp}) and South Africa in cents ({@code ZAc}), which are not ISO currencies: the
 * raw code is always kept, and {@link #iso()} is present only when it is one.
 */
public record QuoteCurrency(String code, Optional<Currency> iso) {

    public QuoteCurrency {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(iso, "iso");
    }

    public static QuoteCurrency of(String code) {
        try {
            return new QuoteCurrency(code, Optional.of(Currency.getInstance(code)));
        } catch (IllegalArgumentException notIso) {
            return new QuoteCurrency(code, Optional.empty());
        }
    }

    /** Whether prices are in a minor unit (pence, cents): {@code GBp}, {@code ZAc}, {@code ILA}. */
    public boolean isPence() {
        return code.equals("GBp") || code.equals("ZAc") || code.equals("ILA");
    }
}
```

`Core.java`:
```java
package io.github.dimazigel.yfinance.instrument;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * The universal tier: present for every asset class on both endpoints (Appendix A, "Universal
 * core"). {@code longName} is optional because futures never have one.
 */
public record Core(
        Symbol symbol,
        String shortName,
        Optional<String> longName,
        QuoteCurrency currency,
        String exchange,
        String fullExchangeName,
        ZoneId exchangeTimezone,
        MarketState marketState,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal previousClose,
        Instant priceTime,
        BigDecimal fiftyTwoWeekLow,
        BigDecimal fiftyTwoWeekHigh,
        BigDecimal fiftyDayAverage,
        BigDecimal twoHundredDayAverage,
        long averageVolume10Day,
        long averageVolume3Month,
        Instant firstTradeDate,
        int priceHint,
        boolean hasPrePostMarketData) {}
```

`Session.java`, `TopOfBook.java`, `PostMarket.java`:
```java
/** The regular-session intraday tier; every class except mutual funds (NAV once a day). */
public record Session(BigDecimal open, BigDecimal dayLow, BigDecimal dayHigh, long volume) {}

/** Best bid/ask. Sizes are optional: futures report them for ~80 % of contracts. */
public record TopOfBook(BigDecimal bid, BigDecimal ask, Optional<Long> bidSize, Optional<Long> askSize) {}

/** After-hours print; present only when there was one (time-of-day, not asset class). */
public record PostMarket(BigDecimal price, BigDecimal change, BigDecimal changePercent, Instant time) {}
```
(each in its own file with the package line and imports for `BigDecimal`, `Optional`, `Instant`).

- [ ] **Step 4: Run the test**

Run: `./gradlew spotlessApply test --tests 'io.github.dimazigel.yfinance.instrument.ValueTypesTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/dimazigel/yfinance/instrument src/test/java/io/github/dimazigel/yfinance/instrument
git commit -m "Add instrument value types: AssetClass, MarketState, QuoteCurrency, Core, Session, TopOfBook, PostMarket" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Assembly framework — `WirePath`, `Unit`, `Kind`, `FieldSpec`, `Payload`, `Resolved`, `Resolver`

**Files:**
- Create: `src/main/java/io/github/dimazigel/yfinance/assembly/package-info.java`, `Source.java`, `WirePath.java`, `Unit.java`, `Kind.java`, `FieldSpec.java`, `Payload.java`, `Resolved.java`, `Resolver.java`
- Test: `src/test/java/io/github/dimazigel/yfinance/assembly/ResolverTest.java`

**Interfaces:**
- Consumes: Jackson `JsonNode`, `ObjectMapper` from `YahooObjectMapper.create()` (existing), `Symbol`.
- Produces:
  - `enum Source { V7, QUOTE_SUMMARY }`; `record WirePath(Source source, String path, Optional<Unit> unit)` with `static WirePath parse(String)` accepting `"v7:marketCap"`, `"qs:price.marketCap"` and a per-path unit override `"v7:dividendYield|PERCENT"` (the same field is a percent on v7 and a fraction on `summaryDetail`; path segments split on `.`; numeric segments index arrays).
  - `enum Unit { RAW, PERCENT, EPOCH_SECONDS, EPOCH_MILLIS, EPOCH_DATE, ISO_DATE }`; `enum Kind { REQUIRED, OPTIONAL, LIST }`.
  - `record FieldSpec(String name, Kind kind, Optional<String> cluster, List<WirePath> paths, Unit unit)` with factories `required(name, unit, paths...)`, `optional(name, unit, paths...)`, `clustered(cluster, name, unit, paths...)` (optional cluster), `requiredCluster(cluster, name, unit, paths...)`, `list(name, paths...)`.
  - `final class Payload { Payload(Symbol symbol, Optional<JsonNode> v7Row, Map<String, JsonNode> modules); Payload withModules(Map<String, JsonNode>); Optional<JsonNode> find(WirePath); boolean hasModules(); }` — `find` unwraps `{raw, fmt}` objects and treats `null`, `""`, `{}`, `[]` as absent.
  - `final class Resolved { List<String> missingRequired(); boolean has(String name); boolean clusterPresent(String cluster); BigDecimal decimal(String); long longValue(String); int intValue(String); String string(String); boolean bool(String); Instant instant(String); LocalDate date(String); JsonNode node(String); Optional<BigDecimal> optDecimal(String); Optional<Long> optLong(String); Optional<Integer> optInt(String); Optional<String> optString(String); Optional<Instant> optInstant(String); Optional<LocalDate> optDate(String); List<JsonNode> list(String); }` — typed getters apply the field's `Unit`; a getter on an absent required field throws `IllegalStateException` (programmer error: builders must check `missingRequired()` first).
  - `final class Resolver { static Resolved resolve(Payload payload, List<FieldSpec> specs); }`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.dimazigel.yfinance.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dimazigel.yfinance.http.YahooObjectMapper;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResolverTest {

    private static final ObjectMapper JSON = YahooObjectMapper.create();
    private static final Symbol AAPL = Symbol.of("AAPL");

    private static JsonNode json(String s) throws Exception {
        return JSON.readTree(s);
    }

    @Test
    void parsesWirePaths() {
        assertThat(WirePath.parse("v7:marketCap")).isEqualTo(new WirePath(Source.V7, "marketCap", Optional.empty()));
        assertThat(WirePath.parse("qs:price.marketCap")).isEqualTo(new WirePath(Source.QUOTE_SUMMARY, "price.marketCap", Optional.empty()));
        assertThat(WirePath.parse("v7:dividendYield|PERCENT")).isEqualTo(new WirePath(Source.V7, "dividendYield", Optional.of(Unit.PERCENT)));
        assertThatThrownBy(() -> WirePath.parse("http:x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WirePath.parse("v7:x|FURLONGS")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void perPathUnitOverrideMakesBothSourcesAgree() throws Exception {   // Review Focus 4 (framework half)
        var spec = List.of(FieldSpec.optional("currentDividend.yield", Unit.RAW,
                "v7:dividendYield|PERCENT", "qs:summaryDetail.dividendYield"));
        var fromV7 = new Payload(AAPL, Optional.of(json("{\"dividendYield\": 0.32}")), Map.of());
        var fromQs = new Payload(AAPL, Optional.empty(), Map.of("summaryDetail", json("{\"dividendYield\": 0.0032}")));

        assertThat(Resolver.resolve(fromV7, spec).optDecimal("currentDividend.yield")).contains(new java.math.BigDecimal("0.0032"));
        assertThat(Resolver.resolve(fromQs, spec).optDecimal("currentDividend.yield")).contains(new java.math.BigDecimal("0.0032"));
    }

    @Test
    void v7WinsThenModulesInDeclaredOrder() throws Exception {
        var payload = new Payload(AAPL, Optional.of(json("{\"marketCap\": 100}")),
                Map.of("price", json("{\"marketCap\": 200}"), "summaryDetail", json("{\"marketCap\": 300}")));
        var specs = List.of(FieldSpec.required("marketCap", Unit.RAW,
                "v7:marketCap", "qs:price.marketCap", "qs:summaryDetail.marketCap"));

        assertThat(Resolver.resolve(payload, specs).decimal("marketCap")).isEqualByComparingTo("100");

        var noV7 = new Payload(AAPL, Optional.empty(), Map.of("summaryDetail", json("{\"marketCap\": 300}")));
        assertThat(Resolver.resolve(noV7, specs).decimal("marketCap")).isEqualByComparingTo("300");
    }

    @Test
    void resolvesRawFmtObjects() throws Exception {   // Review Focus 1
        var payload = new Payload(AAPL, Optional.empty(),
                Map.of("summaryDetail", json("{\"marketCap\": {\"raw\": 2950000000000, \"fmt\": \"2.95T\"},"
                        + "\"trailingPE\": {\"raw\": null, \"fmt\": null}, \"beta\": {}}")));
        var specs = List.of(
                FieldSpec.required("marketCap", Unit.RAW, "qs:summaryDetail.marketCap"),
                FieldSpec.optional("trailingPE", Unit.RAW, "qs:summaryDetail.trailingPE"),
                FieldSpec.optional("beta", Unit.RAW, "qs:summaryDetail.beta"));

        var r = Resolver.resolve(payload, specs);
        assertThat(r.decimal("marketCap")).isEqualByComparingTo("2950000000000");
        assertThat(r.optDecimal("trailingPE")).isEmpty();   // {raw:null} is absent
        assertThat(r.optDecimal("beta")).isEmpty();         // {} is absent
        assertThat(r.missingRequired()).isEmpty();
    }

    @Test
    void tracksMissingRequiredAndRejectsAccessToThem() throws Exception {
        var payload = new Payload(AAPL, Optional.of(json("{\"shortName\": \"Apple\", \"empty\": \"\", \"nul\": null}")), Map.of());
        var specs = List.of(
                FieldSpec.required("shortName", Unit.RAW, "v7:shortName"),
                FieldSpec.required("marketCap", Unit.RAW, "v7:marketCap", "qs:price.marketCap"),
                FieldSpec.required("empty", Unit.RAW, "v7:empty"),
                FieldSpec.required("nul", Unit.RAW, "v7:nul"),
                FieldSpec.optional("beta", Unit.RAW, "v7:beta"));

        var r = Resolver.resolve(payload, specs);
        assertThat(r.missingRequired()).containsExactly("marketCap", "empty", "nul");
        assertThat(r.has("shortName")).isTrue();
        assertThat(r.has("beta")).isFalse();
        assertThatThrownBy(() -> r.decimal("marketCap")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("marketCap");
    }

    @Test
    void appliesUnits() throws Exception {
        var payload = new Payload(AAPL, Optional.of(json("{\"pct\": 0.2083775, \"secs\": 1790347323, \"millis\": 345479400000,"
                + "\"day\": 1783296000, \"iso\": \"2023-09-30\", \"n\": 7, \"big\": 14594180000, \"flag\": true, \"txt\": \"x\"}")), Map.of());
        var specs = List.of(
                FieldSpec.required("pct", Unit.PERCENT, "v7:pct"),
                FieldSpec.required("secs", Unit.EPOCH_SECONDS, "v7:secs"),
                FieldSpec.required("millis", Unit.EPOCH_MILLIS, "v7:millis"),
                FieldSpec.required("day", Unit.EPOCH_DATE, "v7:day"),
                FieldSpec.required("iso", Unit.ISO_DATE, "v7:iso"),
                FieldSpec.required("n", Unit.RAW, "v7:n"),
                FieldSpec.required("big", Unit.RAW, "v7:big"),
                FieldSpec.required("flag", Unit.RAW, "v7:flag"),
                FieldSpec.required("txt", Unit.RAW, "v7:txt"));
        var r = Resolver.resolve(payload, specs);

        assertThat(r.decimal("pct")).isEqualByComparingTo("0.002083775");          // percent -> fraction
        assertThat(r.instant("secs")).isEqualTo(Instant.ofEpochSecond(1790347323));
        assertThat(r.instant("millis")).isEqualTo(Instant.ofEpochMilli(345479400000L));
        assertThat(r.date("day")).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(r.date("iso")).isEqualTo(LocalDate.of(2023, 9, 30));
        assertThat(r.intValue("n")).isEqualTo(7);
        assertThat(r.longValue("big")).isEqualTo(14_594_180_000L);
        assertThat(r.bool("flag")).isTrue();
        assertThat(r.string("txt")).isEqualTo("x");
    }

    @Test
    void clustersArePresentOnlyWhenEveryMemberIs() throws Exception {
        var payload = new Payload(AAPL, Optional.of(json("{\"a\": 1, \"b\": 2, \"c\": 3}")), Map.of());
        var specs = List.of(
                FieldSpec.clustered("full", "a", Unit.RAW, "v7:a"),
                FieldSpec.clustered("full", "b", Unit.RAW, "v7:b"),
                FieldSpec.clustered("partial", "c", Unit.RAW, "v7:c"),
                FieldSpec.clustered("partial", "d", Unit.RAW, "v7:d"));
        var r = Resolver.resolve(payload, specs);
        assertThat(r.clusterPresent("full")).isTrue();
        assertThat(r.clusterPresent("partial")).isFalse();
        assertThat(r.missingRequired()).isEmpty();   // clustered fields are optional
    }

    @Test
    void listsAndArrayIndexes() throws Exception {
        var payload = new Payload(AAPL, Optional.empty(), Map.of(
                "calendarEvents", json("{\"earnings\": {\"earningsDate\": [1793304000, 1793400000]}}"),
                "recommendationTrend", json("{\"trend\": [{\"period\": \"0m\"}, {\"period\": \"-1m\"}]}")));
        var specs = List.of(
                FieldSpec.required("nextEarnings.expected", Unit.EPOCH_SECONDS, "qs:calendarEvents.earnings.earningsDate.0"),
                FieldSpec.list("analysts.recommendationTrend", "qs:recommendationTrend.trend"),
                FieldSpec.list("analysts.secFilings", "qs:secFilings.filings"));
        var r = Resolver.resolve(payload, specs);
        assertThat(r.instant("nextEarnings.expected")).isEqualTo(Instant.ofEpochSecond(1793304000));
        assertThat(r.list("analysts.recommendationTrend")).hasSize(2);
        assertThat(r.list("analysts.secFilings")).isEmpty();       // absent list -> empty, never missing
        assertThat(r.missingRequired()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'io.github.dimazigel.yfinance.assembly.ResolverTest'`
Expected: compilation FAILS — package does not exist.

- [ ] **Step 3: Write the implementation**

`Source.java`, `Unit.java`, `Kind.java`:
```java
public enum Source { V7, QUOTE_SUMMARY }
public enum Unit { RAW, PERCENT, EPOCH_SECONDS, EPOCH_MILLIS, EPOCH_DATE, ISO_DATE }
public enum Kind { REQUIRED, OPTIONAL, LIST }
```

`WirePath.java`:
```java
package io.github.dimazigel.yfinance.assembly;

import java.util.Objects;
import java.util.Optional;

/**
 * Where a field lives on the wire: {@code v7:marketCap} or {@code qs:price.marketCap}. A path may
 * override the field's unit ({@code v7:dividendYield|PERCENT}) because Yahoo serves the same figure
 * as a percent on one endpoint and a fraction on the other.
 */
public record WirePath(Source source, String path, Optional<Unit> unit) {

    public WirePath {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(unit, "unit");
    }

    public static WirePath parse(String spec) {
        int colon = spec.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Wire path must be 'v7:<key>' or 'qs:<module.key>': " + spec);
        }
        String prefix = spec.substring(0, colon);
        String rest = spec.substring(colon + 1);
        Optional<Unit> unit = Optional.empty();
        int bar = rest.indexOf('|');
        if (bar >= 0) {
            try {
                unit = Optional.of(Unit.valueOf(rest.substring(bar + 1)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown unit in wire path " + spec, e);
            }
            rest = rest.substring(0, bar);
        }
        return switch (prefix) {
            case "v7" -> new WirePath(Source.V7, rest, unit);
            case "qs" -> new WirePath(Source.QUOTE_SUMMARY, rest, unit);
            default -> throw new IllegalArgumentException("Unknown wire source '" + prefix + "' in " + spec);
        };
    }

    @Override
    public String toString() {
        return (source == Source.V7 ? "v7:" : "qs:") + path;
    }
}
```

`FieldSpec.java`:
```java
package io.github.dimazigel.yfinance.assembly;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One row of Appendix A: a model field, its kind, its wire paths in precedence order, its unit. */
public record FieldSpec(String name, Kind kind, Optional<String> cluster, List<WirePath> paths, Unit unit) {

    public FieldSpec {
        Objects.requireNonNull(name, "name");
        paths = List.copyOf(paths);
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Field " + name + " needs at least one wire path");
        }
    }

    public static FieldSpec required(String name, Unit unit, String... paths) {
        return new FieldSpec(name, Kind.REQUIRED, Optional.empty(), parse(paths), unit);
    }

    public static FieldSpec optional(String name, Unit unit, String... paths) {
        return new FieldSpec(name, Kind.OPTIONAL, Optional.empty(), parse(paths), unit);
    }

    /** An optional field that is only present when every member of {@code cluster} is. */
    public static FieldSpec clustered(String cluster, String name, Unit unit, String... paths) {
        return new FieldSpec(name, Kind.OPTIONAL, Optional.of(cluster), parse(paths), unit);
    }

    /** A required field that also belongs to a named group, for documentation symmetry with the appendix. */
    public static FieldSpec requiredCluster(String cluster, String name, Unit unit, String... paths) {
        return new FieldSpec(name, Kind.REQUIRED, Optional.of(cluster), parse(paths), unit);
    }

    public static FieldSpec list(String name, String... paths) {
        return new FieldSpec(name, Kind.LIST, Optional.empty(), parse(paths), Unit.RAW);
    }

    private static List<WirePath> parse(String[] paths) {
        return Arrays.stream(paths).map(WirePath::parse).toList();
    }
}
```

`Payload.java`:
```java
package io.github.dimazigel.yfinance.assembly;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The raw JSON Yahoo returned for one symbol: its v7 quote row and any quoteSummary modules. */
public final class Payload {

    private final Symbol symbol;
    private final Optional<JsonNode> v7Row;
    private final Map<String, JsonNode> modules;

    public Payload(Symbol symbol, Optional<JsonNode> v7Row, Map<String, JsonNode> modules) {
        this.symbol = symbol;
        this.v7Row = v7Row;
        this.modules = Map.copyOf(modules);
    }

    public Symbol symbol() {
        return symbol;
    }

    public boolean hasModules() {
        return !modules.isEmpty();
    }

    /** A copy with {@code more} modules added (fallback or detail fetch); existing modules are kept. */
    public Payload withModules(Map<String, JsonNode> more) {
        var merged = new HashMap<>(modules);
        merged.putAll(more);
        return new Payload(symbol, v7Row, merged);
    }

    /** The value at {@code path}, unwrapping Yahoo's {@code {raw, fmt}} objects; empty when absent/blank. */
    public Optional<JsonNode> find(WirePath path) {
        JsonNode cursor;
        String[] segments = path.path().split("\\.");
        int first;
        if (path.source() == Source.V7) {
            if (v7Row.isEmpty()) {
                return Optional.empty();
            }
            cursor = v7Row.get();
            first = 0;
        } else {
            JsonNode module = modules.get(segments[0]);
            if (module == null) {
                return Optional.empty();
            }
            cursor = module;
            first = 1;
        }
        for (int i = first; i < segments.length; i++) {
            String segment = segments[i];
            if (cursor.isArray() && segment.chars().allMatch(Character::isDigit)) {
                cursor = cursor.path(Integer.parseInt(segment));
            } else {
                cursor = cursor.path(segment);
            }
            if (cursor.isMissingNode()) {
                return Optional.empty();
            }
        }
        return present(cursor);
    }

    private static Optional<JsonNode> present(JsonNode node) {
        if (node.isObject() && node.has("raw")) {
            node = node.get("raw"); // Yahoo's {raw, fmt} shape
        }
        if (node.isNull() || node.isMissingNode()) {
            return Optional.empty();
        }
        if (node.isTextual() && node.asText().isBlank()) {
            return Optional.empty();
        }
        if ((node.isObject() || node.isArray()) && node.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(node);
    }
}
```

`Resolved.java`:
```java
package io.github.dimazigel.yfinance.assembly;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Field values after resolution, with typed, unit-aware access. Built only by {@link Resolver}. */
public final class Resolved {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Map<String, FieldSpec> specs;
    private final Map<String, JsonNode> values;
    private final Map<String, Unit> units; // effective unit: the winning path's override, else the field's
    private final List<String> missingRequired;

    Resolved(Map<String, FieldSpec> specs, Map<String, JsonNode> values, Map<String, Unit> units, List<String> missingRequired) {
        this.specs = Map.copyOf(specs);
        this.values = Map.copyOf(values);
        this.units = Map.copyOf(units);
        this.missingRequired = List.copyOf(missingRequired);
    }

    /** Names of REQUIRED fields no source supplied, in spec order. Empty means the record can be built. */
    public List<String> missingRequired() {
        return missingRequired;
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    /** True when every field declared in {@code cluster} resolved. */
    public boolean clusterPresent(String cluster) {
        return specs.values().stream()
                .filter(s -> s.cluster().filter(cluster::equals).isPresent())
                .allMatch(s -> values.containsKey(s.name()));
    }

    // ---- required accessors: absent -> IllegalStateException (check missingRequired() first)

    public JsonNode node(String name) {
        JsonNode node = values.get(name);
        if (node == null) {
            throw new IllegalStateException("Field '" + name + "' was not resolved; check missingRequired() before building");
        }
        return node;
    }

    public BigDecimal decimal(String name) {
        return convertDecimal(name, node(name));
    }

    public long longValue(String name) {
        return node(name).isNumber() ? node(name).longValue() : Long.parseLong(node(name).asText().strip());
    }

    public int intValue(String name) {
        return node(name).isNumber() ? node(name).intValue() : Integer.parseInt(node(name).asText().strip());
    }

    public String string(String name) {
        return node(name).asText();
    }

    public boolean bool(String name) {
        return node(name).asBoolean();
    }

    public Instant instant(String name) {
        return convertInstant(name, node(name));
    }

    public LocalDate date(String name) {
        return convertDate(name, node(name));
    }

    /** Elements of a LIST field; empty when Yahoo omitted it. */
    public List<JsonNode> list(String name) {
        JsonNode node = values.get(name);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var out = new ArrayList<JsonNode>();
        node.forEach(out::add);
        return List.copyOf(out);
    }

    // ---- optional accessors

    public Optional<BigDecimal> optDecimal(String name) {
        return opt(name, n -> convertDecimal(name, n));
    }

    public Optional<Long> optLong(String name) {
        return opt(name, n -> n.isNumber() ? n.longValue() : Long.parseLong(n.asText().strip()));
    }

    public Optional<Integer> optInt(String name) {
        return opt(name, n -> n.isNumber() ? n.intValue() : Integer.parseInt(n.asText().strip()));
    }

    public Optional<String> optString(String name) {
        return opt(name, JsonNode::asText);
    }

    public Optional<Instant> optInstant(String name) {
        return opt(name, n -> convertInstant(name, n));
    }

    public Optional<LocalDate> optDate(String name) {
        return opt(name, n -> convertDate(name, n));
    }

    private <T> Optional<T> opt(String name, Function<JsonNode, T> convert) {
        JsonNode node = values.get(name);
        return node == null ? Optional.empty() : Optional.of(convert.apply(node));
    }

    // ---- unit conversion

    private BigDecimal convertDecimal(String name, JsonNode node) {
        BigDecimal value = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText().strip());
        return unit(name) == Unit.PERCENT ? value.divide(HUNDRED, MathContext.DECIMAL64) : value;
    }

    private Instant convertInstant(String name, JsonNode node) {
        long n = node.longValue();
        return switch (unit(name)) {
            case EPOCH_MILLIS -> Instant.ofEpochMilli(n);
            case EPOCH_SECONDS, EPOCH_DATE, RAW -> Instant.ofEpochSecond(n);
            case ISO_DATE -> LocalDate.parse(node.asText()).atStartOfDay(ZoneOffset.UTC).toInstant();
            case PERCENT -> throw new IllegalStateException("Field '" + name + "' is a percent, not a time");
        };
    }

    private LocalDate convertDate(String name, JsonNode node) {
        return switch (unit(name)) {
            case ISO_DATE -> LocalDate.parse(node.asText().strip());
            case EPOCH_DATE, EPOCH_SECONDS -> LocalDate.ofInstant(Instant.ofEpochSecond(node.longValue()), ZoneOffset.UTC);
            case EPOCH_MILLIS -> LocalDate.ofInstant(Instant.ofEpochMilli(node.longValue()), ZoneOffset.UTC);
            case RAW, PERCENT -> throw new IllegalStateException("Field '" + name + "' has no date unit");
        };
    }

    private Unit unit(String name) {
        return units.getOrDefault(name, Unit.RAW);
    }
}
```

`Resolver.java`:
```java
package io.github.dimazigel.yfinance.assembly;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Executes a field table against a payload: first source that has the value wins. */
public final class Resolver {

    private Resolver() {}

    public static Resolved resolve(Payload payload, List<FieldSpec> specs) {
        var byName = new LinkedHashMap<String, FieldSpec>();
        var values = new LinkedHashMap<String, JsonNode>();
        var units = new LinkedHashMap<String, Unit>();
        var missing = new ArrayList<String>();
        for (FieldSpec spec : specs) {
            byName.put(spec.name(), spec);
            boolean resolved = false;
            for (WirePath path : spec.paths()) {
                Optional<JsonNode> found = payload.find(path);
                if (found.isPresent()) {
                    values.put(spec.name(), found.get());
                    units.put(spec.name(), path.unit().orElse(spec.unit()));
                    resolved = true;
                    break;
                }
            }
            if (!resolved && spec.kind() == Kind.REQUIRED) {
                missing.add(spec.name());
            }
        }
        return new Resolved(byName, values, units, missing);
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew spotlessApply test --tests 'io.github.dimazigel.yfinance.assembly.ResolverTest'`
Expected: PASS (8 tests). If `date("day")` disagrees by a day, check the fixture epoch (1783296000 = 2026-07-06T00:00Z).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/dimazigel/yfinance/assembly src/test/java/io/github/dimazigel/yfinance/assembly
git commit -m "Add assembly framework: FieldSpec tables resolved against raw v7/quoteSummary JSON" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Raw JSON access — `QuoteApi`/`QuoteSummaryApi` return `JsonNode`, `RawQuoteClient`, `YFHttpException`, real fixtures

**Files:**
- Modify: `src/main/java/io/github/dimazigel/yfinance/api/QuoteApi.java`, `api/QuoteSummaryApi.java` (return type → `JsonNode`; the old DTO-typed callers — `QuoteService`, `HoldersService`, `AnalysisService` — keep compiling by switching them to the new `RawQuoteClient` is NOT required: instead add the new `JsonNode` methods alongside the old ones and delete the old ones in Task 16)
- Modify: `src/main/java/io/github/dimazigel/yfinance/http/SyncCallAdapterFactory.java` (throw `YFHttpException` for non-2xx)
- Create: `src/main/java/io/github/dimazigel/yfinance/exception/YFHttpException.java`, `src/main/java/io/github/dimazigel/yfinance/http/RawQuoteClient.java`
- Create: `src/integrationTest/java/io/github/dimazigel/yfinance/CaptureInstrumentFixtures.java` (live, run once), fixtures under `src/test/resources/fixtures/instruments/`
- Test: `src/test/java/io/github/dimazigel/yfinance/http/RawQuoteClientTest.java`, extend `SyncCallAdapterFactoryTest`

**Interfaces:**
- Produces: `QuoteApi.quoteRows(String symbols, boolean formatted): JsonNode` (`@GET("v7/finance/quote")`), `QuoteSummaryApi.modules(String symbol, String modules, boolean formatted, String corsDomain): JsonNode` (`@GET("v10/finance/quoteSummary/{symbol}")`); `YFHttpException extends YFDataException { int status(); String path(); }`; `RawQuoteClient(QuoteApi, QuoteSummaryApi)` with `Map<Symbol, JsonNode> quoteRows(List<Symbol>)` (chunks of 100, keyed by *requested* symbol matched case-insensitively to the row's `symbol`) and `Optional<Map<String, JsonNode>> modules(Symbol, Collection<String> moduleNames)` (empty on 404 = unknown symbol; the map is module name → node from `quoteSummary.result[0]`).
- Fixtures: `v7_<SAFE>.json` = full v7 response for one symbol; `qs_<SAFE>.json` = full quoteSummary response with all 23 modules; `<SAFE>` = symbol with non-alphanumerics replaced by `_`. Symbols: `AAPL`, `PLUG`, `BAC-PL`, `005930.KS`, `TTE.PA`, `SPY`, `CSPX.L`, `GLD`, `VFIAX`, `^GSPC`, `BTC-USD`, `EURUSD=X`, `ES=F`, `RIDE`.

- [ ] **Step 1: Write the failing tests**

Append to `SyncCallAdapterFactoryTest`:
```java
    @Test
    void httpErrorsCarryStatusAndPath() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"Quote not found for symbol: NOPE\"}}}"));

        assertThatThrownBy(() -> api.chart("NOPE", "1d", "1mo", null, null, false, null))
                .isInstanceOf(io.github.dimazigel.yfinance.exception.YFHttpException.class)
                .satisfies(e -> {
                    var http = (io.github.dimazigel.yfinance.exception.YFHttpException) e;
                    assertThat(http.status()).isEqualTo(404);
                    assertThat(http.path()).isEqualTo("/v8/finance/chart/NOPE");
                });
    }
```

`RawQuoteClientTest`:
```java
package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.api.QuoteApi;
import io.github.dimazigel.yfinance.api.QuoteSummaryApi;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RawQuoteClientTest {

    private MockWebServer server;
    private RawQuoteClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new RawQuoteClient(Fixtures.api(server, QuoteApi.class), Fixtures.api(server, QuoteSummaryApi.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void quoteRowsAreKeyedByRequestedSymbolCaseInsensitively() throws Exception {
        server.enqueue(Fixtures.jsonResponse("instruments/v7_AAPL.json"));

        var rows = client.quoteRows(List.of(Symbol.of("aapl"), Symbol.of("NOPE")));

        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols")).isEqualTo("AAPL,NOPE");
        assertThat(rows).containsOnlyKeys(Symbol.of("AAPL"));          // Symbol normalises case; NOPE absent = unknown
        assertThat(rows.get(Symbol.of("AAPL")).path("quoteType").asText()).isEqualTo("EQUITY");
    }

    @Test
    void quoteRowsChunkAtOneHundred() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"quoteResponse\":{\"result\":[],\"error\":null}}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"quoteResponse\":{\"result\":[],\"error\":null}}"));
        var symbols = IntStream.range(0, 150).mapToObj(i -> Symbol.of("S" + i)).toList();

        client.quoteRows(symbols);

        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols").split(",")).hasSize(100);
        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols").split(",")).hasSize(50);
    }

    @Test
    void emptyInputMakesNoRequest() {
        assertThat(client.quoteRows(List.of())).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void modulesReturnsTheModulesOfTheFirstResult() throws Exception {
        server.enqueue(Fixtures.jsonResponse("instruments/qs_AAPL.json"));

        var modules = client.modules(Symbol.of("AAPL"), List.of("price", "summaryDetail", "quoteType"));

        var url = server.takeRequest().getRequestUrl();
        assertThat(url.encodedPath()).isEqualTo("/v10/finance/quoteSummary/AAPL");
        assertThat(url.queryParameter("modules")).isEqualTo("price,summaryDetail,quoteType");
        assertThat(url.queryParameter("formatted")).isEqualTo("false");
        assertThat(modules).isPresent();
        assertThat(modules.get()).containsKeys("price", "summaryDetail", "quoteType", "financialData");
        assertThat(modules.get().get("price").path("regularMarketPrice").isNumber()).isTrue();
    }

    @Test
    void modulesIsEmptyForUnknownSymbolAndPropagatesOtherErrors() {
        server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json")
                .setBody("{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"Quote not found for symbol: NOPE\"}}}"));
        assertThat(client.modules(Symbol.of("NOPE"), List.of("price"))).isEmpty();

        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.modules(Symbol.of("AAPL"), List.of("price")))
                .isInstanceOf(io.github.dimazigel.yfinance.exception.YFHttpException.class);
    }
}
```

- [ ] **Step 2: Capture the fixtures (live, once)**

Create `src/integrationTest/java/io/github/dimazigel/yfinance/CaptureInstrumentFixtures.java`:
```java
package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.http.YahooClientFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Refreshes the real-response fixtures under src/test/resources/fixtures/instruments. Run on demand:
 * {@code CAPTURE_FIXTURES=1 ./gradlew integrationTest --tests '*CaptureInstrumentFixtures*'}.
 * Paced through the library's client; never use raw curl against Yahoo.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "CAPTURE_FIXTURES", matches = "1")
class CaptureInstrumentFixtures {

    static final List<String> SYMBOLS = List.of("AAPL", "PLUG", "BAC-PL", "005930.KS", "TTE.PA", "SPY", "CSPX.L", "GLD",
            "VFIAX", "^GSPC", "BTC-USD", "EURUSD=X", "ES=F", "RIDE");
    static final String MODULES = "assetProfile,summaryProfile,summaryDetail,quoteType,price,financialData,defaultKeyStatistics,"
            + "calendarEvents,secFilings,recommendationTrend,upgradeDowngradeHistory,earningsTrend,earningsHistory,"
            + "majorHoldersBreakdown,institutionOwnership,fundOwnership,insiderHolders,insiderTransactions,"
            + "netSharePurchaseActivity,fundProfile,topHoldings,fundPerformance,esgScores";

    @Test
    void capture() throws Exception {
        var client = YahooClientFactory.apiClient(EndpointConfig.production());
        var out = Path.of("src/test/resources/fixtures/instruments");
        Files.createDirectories(out);
        for (String symbol : SYMBOLS) {
            String safe = symbol.replaceAll("[^A-Za-z0-9]", "_");
            var v7 = HttpUrl.get("https://query1.finance.yahoo.com/v7/finance/quote").newBuilder()
                    .addQueryParameter("symbols", symbol).addQueryParameter("formatted", "false").build();
            try (var resp = client.newCall(new Request.Builder().url(v7).build()).execute()) {
                Files.writeString(out.resolve("v7_" + safe + ".json"), resp.body().string());
            }
            var qs = HttpUrl.get("https://query1.finance.yahoo.com/v10/finance/quoteSummary/" + symbol).newBuilder()
                    .addQueryParameter("modules", MODULES).addQueryParameter("formatted", "false")
                    .addQueryParameter("corsDomain", "finance.yahoo.com").build();
            try (var resp = client.newCall(new Request.Builder().url(qs).build()).execute()) {
                Files.writeString(out.resolve("qs_" + safe + ".json"), resp.body().string()); // 404 bodies are captured too
            }
            Thread.sleep(400);
        }
    }
}
```
Run: `CAPTURE_FIXTURES=1 ./gradlew integrationTest --tests '*CaptureInstrumentFixtures*'`
Expected: 28 files under `src/test/resources/fixtures/instruments/`. Confirm `qs_RIDE.json` is the 404 envelope and `v7_BAC_PL.json` has no `marketCap`.

- [ ] **Step 3: Run the unit tests to verify they fail**

Run: `./gradlew test --tests '*RawQuoteClientTest' --tests '*SyncCallAdapterFactoryTest'`
Expected: compilation FAILS on `RawQuoteClient`, `YFHttpException`, `quoteRows`.

- [ ] **Step 4: Write the implementation**

`YFHttpException.java`:
```java
package io.github.dimazigel.yfinance.exception;

/** Yahoo answered with a non-success HTTP status; {@link #status()} lets callers distinguish 404 from 5xx. */
public class YFHttpException extends YFDataException {

    private final int status;
    private final String path;

    public YFHttpException(int status, String path, String message) {
        super(message);
        this.status = status;
        this.path = path;
    }

    public int status() {
        return status;
    }

    public String path() {
        return path;
    }
}
```

In `SyncCallAdapterFactory.execute`, replace the generic non-success throw:
```java
        if (!response.isSuccessful()) {
            throw new YFHttpException(response.code(), path,
                    "Yahoo Finance returned HTTP " + response.code() + " for " + path + errorDetail(response));
        }
```
(the 429 branch stays a `YFRateLimitException`).

`QuoteApi.java` — add:
```java
    /** Raw rows for the assembler; {@code quoteResponse.result} is an array, absent symbols are omitted. */
    @GET("v7/finance/quote")
    JsonNode quoteRows(@Query("symbols") String symbols, @Query("formatted") boolean formatted);
```
`QuoteSummaryApi.java` — add:
```java
    /** Raw modules for the assembler; {@code quoteSummary.result[0]} maps module name to object. */
    @GET("v10/finance/quoteSummary/{symbol}")
    JsonNode modules(@Path("symbol") String symbol, @Query("modules") String modules,
            @Query("formatted") boolean formatted, @Query("corsDomain") String corsDomain);
```
(Jackson's converter deserialises `JsonNode` directly; `SyncCallAdapterFactory` already handles any body type.)

`RawQuoteClient.java`:
```java
package io.github.dimazigel.yfinance.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.dimazigel.yfinance.api.QuoteApi;
import io.github.dimazigel.yfinance.api.QuoteSummaryApi;
import io.github.dimazigel.yfinance.exception.YFHttpException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Fetches the raw JSON the assembler works on: batched v7 rows and per-symbol quoteSummary modules. */
public final class RawQuoteClient {

    /** Verified in the field survey: Yahoo accepts 100 symbols per v7 request. */
    static final int CHUNK = 100;
    private static final String CORS_DOMAIN = "finance.yahoo.com";

    private final QuoteApi quoteApi;
    private final QuoteSummaryApi quoteSummaryApi;

    public RawQuoteClient(QuoteApi quoteApi, QuoteSummaryApi quoteSummaryApi) {
        this.quoteApi = quoteApi;
        this.quoteSummaryApi = quoteSummaryApi;
    }

    /** One row per known symbol, keyed by the requested {@link Symbol}; unknown symbols are simply absent. */
    public Map<Symbol, JsonNode> quoteRows(List<Symbol> symbols) {
        var rows = new LinkedHashMap<Symbol, JsonNode>();
        List<Symbol> distinct = symbols.stream().distinct().toList();
        for (int i = 0; i < distinct.size(); i += CHUNK) {
            List<Symbol> chunk = distinct.subList(i, Math.min(i + CHUNK, distinct.size()));
            String joined = chunk.stream().map(Symbol::value).collect(Collectors.joining(","));
            JsonNode result = quoteApi.quoteRows(joined, false).path("quoteResponse").path("result");
            for (JsonNode row : result) {
                String reported = row.path("symbol").asText("");
                if (!reported.isBlank()) {
                    rows.put(Symbol.of(reported), row); // Symbol.of upper-cases, matching the requested key
                }
            }
        }
        return rows;
    }

    /** The requested modules for one symbol (module name → object), or empty when Yahoo does not know it. */
    public Optional<Map<String, JsonNode>> modules(Symbol symbol, Collection<String> moduleNames) {
        String joined = String.join(",", moduleNames);
        JsonNode response;
        try {
            response = quoteSummaryApi.modules(symbol.value(), joined, false, CORS_DOMAIN);
        } catch (YFHttpException e) {
            if (e.status() == 404) {
                return Optional.empty();
            }
            throw e;
        }
        JsonNode first = response.path("quoteSummary").path("result").path(0);
        if (first.isMissingNode() || !first.isObject()) {
            return Optional.empty();
        }
        var modules = new LinkedHashMap<String, JsonNode>();
        first.fields().forEachRemaining(entry -> modules.put(entry.getKey(), entry.getValue()));
        return Optional.of(modules);
    }
}
```
`Symbol.of` strips and upper-cases (existing behaviour), so a row reported as `BRK-B` matches a request for `brk-b`. Yahoo may also return a *different* symbol than requested (aliases); such rows are keyed by what Yahoo said and the requested symbol reads as unknown — that is the documented behaviour (design §6.1).

- [ ] **Step 5: Run the tests**

Run: `./gradlew spotlessApply test --tests '*RawQuoteClientTest' --tests '*SyncCallAdapterFactoryTest'`
Expected: PASS. Then `./gradlew build` to confirm nothing else broke (the old DTO methods still exist alongside).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/dimazigel/yfinance/api src/main/java/io/github/dimazigel/yfinance/http src/main/java/io/github/dimazigel/yfinance/exception src/test src/integrationTest src/test/resources/fixtures/instruments
git commit -m "Raw JSON access for the assembler: JsonNode endpoints, RawQuoteClient, YFHttpException, real fixtures" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Phase 1 — Snapshot hierarchy

**Appendix corrections applied by this phase (update the appendix file in Task 18):** `legalType` is sourced only from quoteSummary, so it moves from the ETF *snapshot* table to `EtfDetail` (a required snapshot field with no v7 source would force a fallback request for every ETF and defeat batching). The rows marked *detail* in the MutualFund table belong to `MutualFundDetail` (Task 10). `bookValue`/`priceToBook` stay separate optionals as the appendix says (the design's `BookValueStats` sketch was illustrative).

### Task 5: Universal core — `CoreSpecs`, `CoreBuilder`, `Instrument`, `Unclassified`

**Files:**
- Create: `src/main/java/io/github/dimazigel/yfinance/assembly/specs/package-info.java`, `CoreSpecs.java`; `assembly/build/package-info.java`, `CoreBuilder.java`; `instrument/Instrument.java`, `instrument/Unclassified.java`
- Test: `src/test/java/io/github/dimazigel/yfinance/assembly/build/CoreBuilderTest.java`, `src/test/java/io/github/dimazigel/yfinance/testsupport/InstrumentFixtures.java`

**Interfaces:**
- Consumes: `FieldSpec`, `Resolver`, `Payload`, `Resolved`, `Core`, `QuoteCurrency`, `MarketState`, `AssetClass`.
- Produces: `CoreSpecs.CORE: List<FieldSpec>` (22 rows, names exactly as the appendix "Universal core" table); `CoreBuilder.build(Resolved): Core`; `sealed interface Instrument permits Unclassified { Core core(); AssetClass assetClass(); Instant fetchedAt(); default Symbol symbol() }` (later tasks add records to `permits`); `record Unclassified(Core core, String reportedQuoteType, Optional<AssetClass> attempted, List<String> missing, Optional<Instrument> snapshot, Instant fetchedAt) implements Instrument`; test helper `InstrumentFixtures.v7Row(String symbol): JsonNode`, `qsModules(String symbol): Map<String, JsonNode>`, `payload(String symbol, boolean withModules): Payload`.

- [ ] **Step 1: Write the test helper and the failing test**

`testsupport/InstrumentFixtures.java`:
```java
package io.github.dimazigel.yfinance.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.http.YahooObjectMapper;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Real captured responses from src/test/resources/fixtures/instruments (see CaptureInstrumentFixtures). */
public final class InstrumentFixtures {

    private static final ObjectMapper JSON = YahooObjectMapper.create();

    private InstrumentFixtures() {}

    public static String safe(String symbol) {
        return symbol.replaceAll("[^A-Za-z0-9]", "_");
    }

    /** The single row of the captured v7 response for {@code symbol}. */
    public static JsonNode v7Row(String symbol) {
        try {
            JsonNode result = JSON.readTree(Fixtures.load("instruments/v7_" + safe(symbol) + ".json"))
                    .path("quoteResponse").path("result");
            return result.path(0);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** All modules of the captured quoteSummary response; empty map for a 404 capture. */
    public static Map<String, JsonNode> qsModules(String symbol) {
        try {
            JsonNode first = JSON.readTree(Fixtures.load("instruments/qs_" + safe(symbol) + ".json"))
                    .path("quoteSummary").path("result").path(0);
            var modules = new LinkedHashMap<String, JsonNode>();
            first.fields().forEachRemaining(e -> modules.put(e.getKey(), e.getValue()));
            return modules;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static Payload payload(String symbol, boolean withModules) {
        return new Payload(Symbol.of(symbol), Optional.of(v7Row(symbol)), withModules ? qsModules(symbol) : Map.of());
    }

    /** A v7 response body containing the captured rows for the given symbols, for MockWebServer. */
    public static String v7Response(String... symbols) {
        var rows = JSON.createArrayNode();
        for (String s : symbols) {
            JsonNode row = v7Row(s);
            if (!row.isMissingNode()) {
                rows.add(row);
            }
        }
        var body = JSON.createObjectNode();
        body.putObject("quoteResponse").set("result", rows);
        return body.toString();
    }
}
```

`CoreBuilderTest.java`:
```java
package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.CoreSpecs;
import io.github.dimazigel.yfinance.http.YahooObjectMapper;
import io.github.dimazigel.yfinance.instrument.MarketState;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoreBuilderTest {

    @Test
    void buildsTheUniversalCoreFromAnEquityRow() {
        var resolved = Resolver.resolve(InstrumentFixtures.payload("AAPL", false), CoreSpecs.CORE);
        assertThat(resolved.missingRequired()).isEmpty();

        var core = CoreBuilder.build(resolved);
        assertThat(core.symbol()).isEqualTo(Symbol.of("AAPL"));
        assertThat(core.shortName()).isEqualTo("Apple Inc.");
        assertThat(core.longName()).contains("Apple Inc.");
        assertThat(core.currency().iso()).contains(java.util.Currency.getInstance("USD"));
        assertThat(core.exchange()).isEqualTo("NMS");
        assertThat(core.exchangeTimezone()).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(core.marketState()).isNotEqualTo(MarketState.OTHER);
        assertThat(core.price()).isPositive();
        assertThat(core.changePercent().abs()).isLessThan(java.math.BigDecimal.ONE); // a fraction, not a percent
        assertThat(core.priceTime()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(core.firstTradeDate()).isEqualTo(java.time.Instant.ofEpochMilli(345479400000L));
        assertThat(core.averageVolume3Month()).isPositive();
        assertThat(core.hasPrePostMarketData()).isTrue();
    }

    @Test
    void coreIsCompleteForEveryClassAndLongNameIsEmptyForFutures() {
        for (String symbol : new String[] {"SPY", "VFIAX", "^GSPC", "BTC-USD", "EURUSD=X", "ES=F"}) {
            var resolved = Resolver.resolve(InstrumentFixtures.payload(symbol, false), CoreSpecs.CORE);
            assertThat(resolved.missingRequired()).as(symbol).isEmpty();
            var core = CoreBuilder.build(resolved);
            assertThat(core.symbol().value()).isEqualTo(symbol.toUpperCase(java.util.Locale.ROOT));
            if (symbol.equals("ES=F")) {
                assertThat(core.longName()).isEmpty();
            }
        }
    }

    @Test
    void coreFallsBackToQuoteSummaryModules() throws Exception {
        // No v7 row at all: price/quoteType/summaryDetail must be enough for the core (except firstTradeDate, v7-only)
        var modules = InstrumentFixtures.qsModules("AAPL");
        var resolved = Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.empty(), modules), CoreSpecs.CORE);
        assertThat(resolved.missingRequired()).containsExactly("firstTradeDate", "hasPrePostMarketData");
    }

    @Test
    void penceQuotedCurrencyIsKeptNotNulled() throws Exception {
        var row = YahooObjectMapper.create().readTree(InstrumentFixtures.v7Row("AAPL").toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) row).put("currency", "GBp");
        var resolved = Resolver.resolve(new Payload(Symbol.of("BP.L"), Optional.of(row), Map.of()), CoreSpecs.CORE);
        var core = CoreBuilder.build(resolved);
        assertThat(core.currency().code()).isEqualTo("GBp");
        assertThat(core.currency().iso()).isEmpty();
        assertThat(core.currency().isPence()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*CoreBuilderTest'`
Expected: compilation FAILS — `CoreSpecs`, `CoreBuilder` missing.

- [ ] **Step 3: Write the implementation**

`assembly/specs/CoreSpecs.java`:
```java
package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.optional;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.EPOCH_MILLIS;
import static io.github.dimazigel.yfinance.assembly.Unit.EPOCH_SECONDS;
import static io.github.dimazigel.yfinance.assembly.Unit.PERCENT;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Appendix A, "Universal core": present for every asset class. */
public final class CoreSpecs {

    private CoreSpecs() {}

    public static final List<FieldSpec> CORE = List.of(
            required("symbol", RAW, "v7:symbol", "qs:quoteType.symbol"),
            required("shortName", RAW, "v7:shortName", "qs:quoteType.shortName", "qs:price.shortName"),
            optional("longName", RAW, "v7:longName", "qs:quoteType.longName", "qs:price.longName"),
            required("currency", RAW, "v7:currency", "qs:price.currency", "qs:summaryDetail.currency"),
            required("exchange", RAW, "v7:exchange", "qs:quoteType.exchange", "qs:price.exchange"),
            required("fullExchangeName", RAW, "v7:fullExchangeName", "qs:price.exchangeName"),
            required("exchangeTimezone", RAW, "v7:exchangeTimezoneName", "qs:quoteType.timeZoneFullName"),
            required("marketState", RAW, "v7:marketState", "qs:price.marketState"),
            required("price", RAW, "v7:regularMarketPrice", "qs:price.regularMarketPrice"),
            required("change", RAW, "v7:regularMarketChange", "qs:price.regularMarketChange"),
            required("changePercent", PERCENT, "v7:regularMarketChangePercent", "qs:price.regularMarketChangePercent"),
            required("previousClose", RAW, "v7:regularMarketPreviousClose", "qs:price.regularMarketPreviousClose", "qs:summaryDetail.previousClose"),
            required("priceTime", EPOCH_SECONDS, "v7:regularMarketTime", "qs:price.regularMarketTime"),
            required("fiftyTwoWeekLow", RAW, "v7:fiftyTwoWeekLow", "qs:summaryDetail.fiftyTwoWeekLow"),
            required("fiftyTwoWeekHigh", RAW, "v7:fiftyTwoWeekHigh", "qs:summaryDetail.fiftyTwoWeekHigh"),
            required("fiftyDayAverage", RAW, "v7:fiftyDayAverage", "qs:summaryDetail.fiftyDayAverage"),
            required("twoHundredDayAverage", RAW, "v7:twoHundredDayAverage", "qs:summaryDetail.twoHundredDayAverage"),
            required("averageVolume10Day", RAW, "v7:averageDailyVolume10Day", "qs:summaryDetail.averageDailyVolume10Day", "qs:price.averageDailyVolume10Day"),
            required("averageVolume3Month", RAW, "v7:averageDailyVolume3Month", "qs:summaryDetail.averageVolume", "qs:price.averageDailyVolume3Month"),
            required("firstTradeDate", EPOCH_MILLIS, "v7:firstTradeDateMilliseconds"),
            required("priceHint", RAW, "v7:priceHint", "qs:price.priceHint"),
            required("hasPrePostMarketData", RAW, "v7:hasPrePostMarketData"));
}
```
Note: `changePercent` is a percent on **both** endpoints (the survey's `regularMarketChangePercent` is `0.208…` for a 0.2 % move), so the field-level `PERCENT` unit applies to every path.

`assembly/build/CoreBuilder.java`:
```java
package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.Core;
import io.github.dimazigel.yfinance.instrument.MarketState;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.ZoneId;

/** {@link Resolved} → {@link Core}. Callers must have checked {@code missingRequired()} first. */
public final class CoreBuilder {

    private CoreBuilder() {}

    public static Core build(Resolved r) {
        return new Core(
                Symbol.of(r.string("symbol")),
                r.string("shortName"),
                r.optString("longName"),
                QuoteCurrency.of(r.string("currency")),
                r.string("exchange"),
                r.string("fullExchangeName"),
                ZoneId.of(r.string("exchangeTimezone")),
                MarketState.fromWire(r.string("marketState")),
                r.decimal("price"),
                r.decimal("change"),
                r.decimal("changePercent"),
                r.decimal("previousClose"),
                r.instant("priceTime"),
                r.decimal("fiftyTwoWeekLow"),
                r.decimal("fiftyTwoWeekHigh"),
                r.decimal("fiftyDayAverage"),
                r.decimal("twoHundredDayAverage"),
                r.longValue("averageVolume10Day"),
                r.longValue("averageVolume3Month"),
                r.instant("firstTradeDate"),
                r.intValue("priceHint"),
                r.bool("hasPrePostMarketData"));
    }
}
```

`instrument/Instrument.java`:
```java
package io.github.dimazigel.yfinance.instrument;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;

/**
 * An instrument as Yahoo describes it, typed by asset class. Sealed: a {@code switch} over it is
 * exhaustive. Every class carries the universal {@link Core}; class records add what their class
 * guarantees (non-null) or sometimes has ({@code Optional}); see the design's Appendix A.
 */
public sealed interface Instrument permits Unclassified {

    Core core();

    AssetClass assetClass();

    /** When this snapshot was fetched (wall clock); the as-of for storage. */
    Instant fetchedAt();

    default Symbol symbol() {
        return core().symbol();
    }
}
```

`instrument/Unclassified.java`:
```java
package io.github.dimazigel.yfinance.instrument;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The downgrade target: only the universal core is known. Either Yahoo's {@code quoteType} is one
 * the model does not type ({@code attempted} empty), or it named a class whose guarantee failed
 * ({@code attempted} = that class, {@code missing} = the fields no source supplied).
 *
 * @param snapshot the classified snapshot when only a <em>detail</em> request failed (design §4.2)
 */
public record Unclassified(
        Core core,
        String reportedQuoteType,
        Optional<AssetClass> attempted,
        List<String> missing,
        Optional<Instrument> snapshot,
        Instant fetchedAt) implements Instrument {

    public Unclassified {
        missing = List.copyOf(missing);
    }

    @Override
    public AssetClass assetClass() {
        return AssetClass.UNCLASSIFIED;
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew spotlessApply test --tests '*CoreBuilderTest'`
Expected: PASS (4 tests). If `coreFallsBackToQuoteSummaryModules` reports different missing names, the fixture's `quoteType`/`price` modules lack a key the appendix says they have — check the fixture before touching the spec.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/dimazigel/yfinance/assembly src/main/java/io/github/dimazigel/yfinance/instrument src/test
git commit -m "Universal core: CoreSpecs, CoreBuilder, sealed Instrument root, Unclassified" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Equity snapshot — tiers, `EquitySpecs`, `Equity`, `EquityBuilder`

**Files:**
- Create: `instrument/IntradayTraded.java`, `instrument/Quoted.java`, `instrument/Equity.java`; `assembly/specs/SessionSpecs.java`, `BookSpecs.java`, `PostMarketSpecs.java`, `EquitySpecs.java`; `assembly/build/EquityBuilder.java`, `assembly/build/TierBuilders.java`
- Modify: `instrument/Instrument.java` (`permits Equity, Unclassified`)
- Test: `src/test/java/io/github/dimazigel/yfinance/assembly/build/EquityBuilderTest.java`

**Interfaces:**
- Produces: `sealed interface IntradayTraded permits Equity { Session session(); }`; `sealed interface Quoted permits Equity { Optional<TopOfBook> book(); }` (permits grow in Tasks 7–8); `SessionSpecs.SESSION`, `BookSpecs.BOOK` (cluster `book`), `PostMarketSpecs.POST_MARKET` (cluster `postMarket`), `EquitySpecs.SNAPSHOT` (= CORE + SESSION + BOOK + POST_MARKET + equity rows); `TierBuilders.session(Resolved): Session`, `book(Resolved): Optional<TopOfBook>`, `postMarket(Resolved): Optional<PostMarket>`; `EquityBuilder.build(Resolved, Instant): Equity`.
- `record Equity(Core core, Session session, Optional<TopOfBook> book, Valuation valuation, NextEarnings nextEarnings, Optional<BigDecimal> bookValue, Optional<BigDecimal> priceToBook, Optional<BigDecimal> trailingEps, Optional<BigDecimal> forwardEps, Optional<BigDecimal> forwardPE, Optional<BigDecimal> trailingPE, Optional<TrailingDividend> trailingDividend, Optional<CurrentDividend> currentDividend, Optional<CurrentYearEps> currentYearEps, Optional<String> averageAnalystRating, Optional<PostMarket> postMarket, Instant fetchedAt) implements Instrument, IntradayTraded, Quoted` with nested `record Valuation(BigDecimal marketCap, long sharesOutstanding, long impliedSharesOutstanding, QuoteCurrency financialCurrency)`, `record NextEarnings(Instant expected, Instant windowStart, Instant windowEnd, boolean isEstimate)`, `record TrailingDividend(BigDecimal rate, BigDecimal yield)`, `record CurrentDividend(BigDecimal rate, BigDecimal yield)`, `record CurrentYearEps(BigDecimal eps, BigDecimal priceToEps)`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.EquitySpecs;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EquityBuilderTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Test
    void appleHasEveryGuaranteeAndMostOptionals() {
        var r = Resolver.resolve(InstrumentFixtures.payload("AAPL", false), EquitySpecs.SNAPSHOT);
        assertThat(r.missingRequired()).isEmpty();

        Equity aapl = EquityBuilder.build(r, NOW);
        assertThat(aapl.assetClass()).isEqualTo(AssetClass.EQUITY);
        assertThat(aapl.valuation().marketCap()).isGreaterThan(new BigDecimal("1000000000000"));
        assertThat(aapl.valuation().sharesOutstanding()).isPositive();
        assertThat(aapl.valuation().financialCurrency().code()).isEqualTo("USD");
        assertThat(aapl.nextEarnings().windowStart()).isBeforeOrEqualTo(aapl.nextEarnings().windowEnd());
        assertThat(aapl.session().volume()).isPositive();
        assertThat(aapl.book()).isPresent();
        assertThat(aapl.bookValue()).isPresent();
        assertThat(aapl.trailingEps()).isPresent();
        assertThat(aapl.trailingPE()).isPresent();
        assertThat(aapl.averageAnalystRating()).isPresent();
        assertThat(aapl.currentYearEps()).isPresent();
        assertThat(aapl.fetchedAt()).isEqualTo(NOW);
    }

    @Test
    void lossMakingEquityHasNoTrailingPeOrDividend() {
        var r = Resolver.resolve(InstrumentFixtures.payload("PLUG", false), EquitySpecs.SNAPSHOT);
        assertThat(r.missingRequired()).isEmpty();
        Equity plug = EquityBuilder.build(r, NOW);
        assertThat(plug.trailingPE()).isEmpty();
        assertThat(plug.currentDividend()).isEmpty();
        assertThat(plug.valuation().marketCap()).isPositive();   // still a full equity
    }

    @Test
    void samsungLacksBookValueButIsStillAnEquity() {
        var r = Resolver.resolve(InstrumentFixtures.payload("005930.KS", true), EquitySpecs.SNAPSHOT);
        assertThat(r.missingRequired()).as("Intrinsic rule: nothing Samsung lacks is required").isEmpty();
        Equity samsung = EquityBuilder.build(r, NOW);
        assertThat(samsung.bookValue()).isEmpty();
        assertThat(samsung.trailingEps()).isEmpty();
        assertThat(samsung.valuation().marketCap()).isPositive();
    }

    @Test
    void preferredShareFailsTheMarketCapGuarantee() {
        var r = Resolver.resolve(InstrumentFixtures.payload("BAC-PL", true), EquitySpecs.SNAPSHOT);
        assertThat(r.missingRequired()).contains("marketCap");   // -> Unclassified in InstrumentService
    }

    @Test
    void currentDividendYieldIsAFractionFromEitherSource() {   // Review Focus 4
        ObjectNode row = InstrumentFixtures.v7Row("AAPL").deepCopy();
        row.put("dividendRate", 1.08).put("dividendYield", 0.32);            // v7: percent
        var fromV7 = EquityBuilder.build(Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.of(row), Map.of()), EquitySpecs.SNAPSHOT), NOW);
        assertThat(fromV7.currentDividend()).isPresent();
        assertThat(fromV7.currentDividend().get().yield()).isEqualByComparingTo("0.0032");

        row.remove("dividendYield");
        row.remove("dividendRate");
        var modules = new java.util.HashMap<>(InstrumentFixtures.qsModules("AAPL"));
        ObjectNode sd = modules.get("summaryDetail").deepCopy();
        sd.put("dividendRate", 1.08).put("dividendYield", 0.0032);          // summaryDetail: fraction
        modules.put("summaryDetail", sd);
        var fromQs = EquityBuilder.build(Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.of(row), modules), EquitySpecs.SNAPSHOT), NOW);
        assertThat(fromQs.currentDividend().get().yield()).isEqualByComparingTo("0.0032");
    }

    @Test
    void postMarketIsAllOrNothing() {
        ObjectNode row = InstrumentFixtures.v7Row("AAPL").deepCopy();
        row.remove("postMarketTime");   // three of four present
        var r = Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.of(row), Map.of()), EquitySpecs.SNAPSHOT);
        assertThat(EquityBuilder.build(r, NOW).postMarket()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*EquityBuilderTest'`
Expected: compilation FAILS.

- [ ] **Step 3: Write the implementation**

`instrument/IntradayTraded.java` / `Quoted.java`:
```java
/** Classes with a regular intraday session (open/high/low/volume): all but mutual funds. */
public sealed interface IntradayTraded permits Equity { Session session(); }

/** Classes Yahoo quotes with a bid/ask; present for almost all of them, hence Optional (design D5). */
public sealed interface Quoted permits Equity { Optional<TopOfBook> book(); }
```
Update `Instrument`: `permits Equity, Unclassified`.

`instrument/Equity.java`:
```java
package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** A common stock (Yahoo {@code quoteType} EQUITY). Non-null fields are the Appendix A "R" rows. */
public record Equity(
        Core core,
        Session session,
        Optional<TopOfBook> book,
        Valuation valuation,
        NextEarnings nextEarnings,
        Optional<BigDecimal> bookValue,
        Optional<BigDecimal> priceToBook,
        Optional<BigDecimal> trailingEps,
        Optional<BigDecimal> forwardEps,
        Optional<BigDecimal> forwardPE,
        Optional<BigDecimal> trailingPE,
        Optional<TrailingDividend> trailingDividend,
        Optional<CurrentDividend> currentDividend,
        Optional<CurrentYearEps> currentYearEps,
        Optional<String> averageAnalystRating,
        Optional<PostMarket> postMarket,
        Instant fetchedAt) implements Instrument, IntradayTraded, Quoted {

    @Override
    public AssetClass assetClass() {
        return AssetClass.EQUITY;
    }

    /** Guaranteed valuation facts; a symbol lacking any of them is not treated as an equity. */
    public record Valuation(BigDecimal marketCap, long sharesOutstanding, long impliedSharesOutstanding, QuoteCurrency financialCurrency) {}

    /** Next earnings release window; {@code isEstimate} when Yahoo has not confirmed the date. */
    public record NextEarnings(Instant expected, Instant windowStart, Instant windowEnd, boolean isEstimate) {}

    /** Dividends paid over the trailing twelve months; yield is a fraction. */
    public record TrailingDividend(BigDecimal rate, BigDecimal yield) {}

    /** Forward (declared) dividend; yield is a fraction. Absent for non-payers. */
    public record CurrentDividend(BigDecimal rate, BigDecimal yield) {}

    /** Consensus EPS for the current fiscal year and the price relative to it. */
    public record CurrentYearEps(BigDecimal eps, BigDecimal priceToEps) {}
}
```

`assembly/specs/SessionSpecs.java`, `BookSpecs.java`, `PostMarketSpecs.java`:
```java
public final class SessionSpecs {
    public static final List<FieldSpec> SESSION = List.of(
            required("open", RAW, "v7:regularMarketOpen", "qs:price.regularMarketOpen", "qs:summaryDetail.open"),
            required("dayLow", RAW, "v7:regularMarketDayLow", "qs:price.regularMarketDayLow", "qs:summaryDetail.dayLow"),
            required("dayHigh", RAW, "v7:regularMarketDayHigh", "qs:price.regularMarketDayHigh", "qs:summaryDetail.dayHigh"),
            required("volume", RAW, "v7:regularMarketVolume", "qs:price.regularMarketVolume", "qs:summaryDetail.volume"));
}
public final class BookSpecs {
    public static final List<FieldSpec> BOOK = List.of(
            clustered("book", "bid", RAW, "v7:bid", "qs:summaryDetail.bid"),
            clustered("book", "ask", RAW, "v7:ask", "qs:summaryDetail.ask"),
            optional("bidSize", RAW, "v7:bidSize", "qs:summaryDetail.bidSize"),
            optional("askSize", RAW, "v7:askSize", "qs:summaryDetail.askSize"));
}
public final class PostMarketSpecs {
    public static final List<FieldSpec> POST_MARKET = List.of(
            clustered("postMarket", "postMarketPrice", RAW, "v7:postMarketPrice", "qs:price.postMarketPrice"),
            clustered("postMarket", "postMarketChange", RAW, "v7:postMarketChange", "qs:price.postMarketChange"),
            clustered("postMarket", "postMarketChangePercent", PERCENT, "v7:postMarketChangePercent", "qs:price.postMarketChangePercent"),
            clustered("postMarket", "postMarketTime", EPOCH_SECONDS, "v7:postMarketTime", "qs:price.postMarketTime"));
}
```
(each a `final class` with private constructor and the static imports used in `CoreSpecs`).

`assembly/specs/EquitySpecs.java`:
```java
public final class EquitySpecs {
    private EquitySpecs() {}

    private static final List<FieldSpec> OWN = List.of(
            required("marketCap", RAW, "v7:marketCap", "qs:price.marketCap", "qs:summaryDetail.marketCap"),
            required("sharesOutstanding", RAW, "v7:sharesOutstanding", "qs:defaultKeyStatistics.sharesOutstanding"),
            required("impliedSharesOutstanding", RAW, "v7:impliedSharesOutstanding", "qs:defaultKeyStatistics.impliedSharesOutstanding"),
            required("financialCurrency", RAW, "v7:financialCurrency", "qs:financialData.financialCurrency"),
            requiredCluster("nextEarnings", "nextEarnings.expected", EPOCH_SECONDS, "v7:earningsTimestamp", "v7:earningsTimestampStart", "qs:calendarEvents.earnings.earningsDate.0"),
            requiredCluster("nextEarnings", "nextEarnings.windowStart", EPOCH_SECONDS, "v7:earningsTimestampStart"),
            requiredCluster("nextEarnings", "nextEarnings.windowEnd", EPOCH_SECONDS, "v7:earningsTimestampEnd"),
            requiredCluster("nextEarnings", "nextEarnings.isEstimate", RAW, "v7:isEarningsDateEstimate"),
            optional("bookValue", RAW, "v7:bookValue", "qs:defaultKeyStatistics.bookValue"),
            optional("priceToBook", RAW, "v7:priceToBook", "qs:defaultKeyStatistics.priceToBook"),
            optional("trailingEps", RAW, "v7:epsTrailingTwelveMonths", "qs:defaultKeyStatistics.trailingEps"),
            optional("forwardEps", RAW, "v7:epsForward", "qs:defaultKeyStatistics.forwardEps"),
            optional("forwardPE", RAW, "v7:forwardPE", "qs:summaryDetail.forwardPE", "qs:defaultKeyStatistics.forwardPE"),
            optional("trailingPE", RAW, "v7:trailingPE", "qs:summaryDetail.trailingPE"),
            clustered("trailingDividend", "trailingDividend.rate", RAW, "v7:trailingAnnualDividendRate", "qs:summaryDetail.trailingAnnualDividendRate"),
            clustered("trailingDividend", "trailingDividend.yield", RAW, "v7:trailingAnnualDividendYield", "qs:summaryDetail.trailingAnnualDividendYield"),
            clustered("currentDividend", "currentDividend.rate", RAW, "v7:dividendRate", "qs:summaryDetail.dividendRate"),
            clustered("currentDividend", "currentDividend.yield", RAW, "v7:dividendYield|PERCENT", "qs:summaryDetail.dividendYield"),
            clustered("currentYearEps", "currentYearEps.eps", RAW, "v7:epsCurrentYear"),
            clustered("currentYearEps", "currentYearEps.priceToEps", RAW, "v7:priceEpsCurrentYear"),
            optional("averageAnalystRating", RAW, "v7:averageAnalystRating"));

    public static final List<FieldSpec> SNAPSHOT = concat(CoreSpecs.CORE, SessionSpecs.SESSION, BookSpecs.BOOK, PostMarketSpecs.POST_MARKET, OWN);

    @SafeVarargs
    static List<FieldSpec> concat(List<FieldSpec>... parts) {
        return java.util.Arrays.stream(parts).flatMap(List::stream).toList();
    }
}
```
Put `concat` in a small package-private `Specs` helper class instead if more than one specs class needs it (Tasks 7–8 do) — create `assembly/specs/Specs.java` with `static List<FieldSpec> concat(List<FieldSpec>... parts)` now and use it everywhere.

`assembly/build/TierBuilders.java`:
```java
package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.PostMarket;
import io.github.dimazigel.yfinance.instrument.Session;
import io.github.dimazigel.yfinance.instrument.TopOfBook;
import java.util.Optional;

/** Builders for the tiers several classes share. */
final class TierBuilders {

    private TierBuilders() {}

    static Session session(Resolved r) {
        return new Session(r.decimal("open"), r.decimal("dayLow"), r.decimal("dayHigh"), r.longValue("volume"));
    }

    static Optional<TopOfBook> book(Resolved r) {
        if (!r.clusterPresent("book")) {
            return Optional.empty();
        }
        return Optional.of(new TopOfBook(r.decimal("bid"), r.decimal("ask"), r.optLong("bidSize"), r.optLong("askSize")));
    }

    static Optional<PostMarket> postMarket(Resolved r) {
        if (!r.clusterPresent("postMarket")) {
            return Optional.empty();
        }
        return Optional.of(new PostMarket(r.decimal("postMarketPrice"), r.decimal("postMarketChange"),
                r.decimal("postMarketChangePercent"), r.instant("postMarketTime")));
    }
}
```

`assembly/build/EquityBuilder.java`:
```java
package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import java.time.Instant;
import java.util.Optional;

public final class EquityBuilder {

    private EquityBuilder() {}

    public static Equity build(Resolved r, Instant fetchedAt) {
        return new Equity(
                CoreBuilder.build(r),
                TierBuilders.session(r),
                TierBuilders.book(r),
                new Equity.Valuation(r.decimal("marketCap"), r.longValue("sharesOutstanding"),
                        r.longValue("impliedSharesOutstanding"), QuoteCurrency.of(r.string("financialCurrency"))),
                new Equity.NextEarnings(r.instant("nextEarnings.expected"), r.instant("nextEarnings.windowStart"),
                        r.instant("nextEarnings.windowEnd"), r.bool("nextEarnings.isEstimate")),
                r.optDecimal("bookValue"),
                r.optDecimal("priceToBook"),
                r.optDecimal("trailingEps"),
                r.optDecimal("forwardEps"),
                r.optDecimal("forwardPE"),
                r.optDecimal("trailingPE"),
                r.clusterPresent("trailingDividend")
                        ? Optional.of(new Equity.TrailingDividend(r.decimal("trailingDividend.rate"), r.decimal("trailingDividend.yield")))
                        : Optional.empty(),
                r.clusterPresent("currentDividend")
                        ? Optional.of(new Equity.CurrentDividend(r.decimal("currentDividend.rate"), r.decimal("currentDividend.yield")))
                        : Optional.empty(),
                r.clusterPresent("currentYearEps")
                        ? Optional.of(new Equity.CurrentYearEps(r.decimal("currentYearEps.eps"), r.decimal("currentYearEps.priceToEps")))
                        : Optional.empty(),
                r.optString("averageAnalystRating"),
                TierBuilders.postMarket(r),
                fetchedAt);
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew spotlessApply test --tests '*EquityBuilderTest'`
Expected: PASS (6 tests). If `samsungLacksBookValueButIsStillAnEquity` reports a missing required field, the Intrinsic rule was mis-encoded — that field must become `optional(...)` and the appendix row `O` (record the change for Task 18).

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "Equity snapshot: session/book/post-market tiers, EquitySpecs, Equity record, EquityBuilder" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Fund snapshots — `Fund` tier, `Etf`, `MutualFund`, their specs and builders

**Files:**
- Create: `instrument/Fund.java`, `instrument/Etf.java`, `instrument/MutualFund.java`; `assembly/specs/EtfSpecs.java`, `MutualFundSpecs.java`; `assembly/build/EtfBuilder.java`, `MutualFundBuilder.java`
- Modify: `Instrument`, `IntradayTraded`, `Quoted` permits lists
- Test: `src/test/java/io/github/dimazigel/yfinance/assembly/build/FundBuildersTest.java`

**Interfaces:**
- Produces: `sealed interface Fund permits Etf, MutualFund { BigDecimal ytdReturn(); BigDecimal threeMonthReturn(); }`; `record Etf(Core core, Session session, Optional<TopOfBook> book, BigDecimal ytdReturn, BigDecimal threeMonthReturn, Optional<BigDecimal> netAssets, Optional<BigDecimal> expenseRatio, Optional<BigDecimal> yield, Optional<BigDecimal> navPrice, Optional<BigDecimal> beta3Year, Optional<BigDecimal> trailingThreeMonthNavReturns, Optional<BigDecimal> trailingPE, Optional<EquityLikeStats> equityLikeStats, Optional<TrailingDividend> trailingDividend, Optional<PostMarket> postMarket, Instant fetchedAt) implements Instrument, IntradayTraded, Quoted, Fund` with nested `record EquityLikeStats(BigDecimal bookValue, BigDecimal priceToBook, long sharesOutstanding, QuoteCurrency financialCurrency)`; `record MutualFund(Core core, BigDecimal netAssets, BigDecimal expenseRatio, BigDecimal yield, BigDecimal dividendRate, BigDecimal ytdReturn, BigDecimal threeMonthReturn, Optional<Etf.EquityLikeStats> equityLikeStats, Optional<BigDecimal> trailingPE, Optional<TrailingDividend> trailingDividend, Instant fetchedAt) implements Instrument, Fund`. `TrailingDividend` moves from `Equity` to a top-level `instrument/TrailingDividend.java` (shared by three classes); update Task 6's references accordingly. `EtfSpecs.SNAPSHOT`, `MutualFundSpecs.SNAPSHOT`, `EtfBuilder.build`, `MutualFundBuilder.build`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.EtfSpecs;
import io.github.dimazigel.yfinance.assembly.specs.MutualFundSpecs;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Fund;
import io.github.dimazigel.yfinance.instrument.IntradayTraded;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FundBuildersTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Test
    void usEtfHasFundMetricsFromV7() {
        var r = Resolver.resolve(InstrumentFixtures.payload("SPY", false), EtfSpecs.SNAPSHOT);
        assertThat(r.missingRequired()).isEmpty();          // ytd/3m returns come from v7 for US ETFs
        Etf spy = EtfBuilder.build(r, NOW);
        assertThat(spy.expenseRatio()).isPresent();
        assertThat(spy.netAssets()).isPresent();
        assertThat(spy.session().volume()).isPositive();
        assertThat(spy.book()).isPresent();
        assertThat(spy.ytdReturn()).isNotNull();
        assertThat(spy).isInstanceOf(Fund.class).isInstanceOf(IntradayTraded.class);
    }

    @Test
    void ucitsEtfNeedsQuoteSummaryForReturnsAndHasNoExpenseRatioAnywhere() {
        var v7Only = Resolver.resolve(InstrumentFixtures.payload("CSPX.L", false), EtfSpecs.SNAPSHOT);
        assertThat(v7Only.missingRequired()).containsExactlyInAnyOrder("ytdReturn", "threeMonthReturn"); // fallback trigger

        var withModules = Resolver.resolve(InstrumentFixtures.payload("CSPX.L", true), EtfSpecs.SNAPSHOT);
        assertThat(withModules.missingRequired()).isEmpty();   // fundPerformance.trailingReturns fills them
        Etf cspx = EtfBuilder.build(withModules, NOW);
        assertThat(cspx.expenseRatio()).as("UCITS listings lack it in every source").isEmpty();
        assertThat(cspx.ytdReturn()).isNotNull();
    }

    @Test
    void goldEtfHasNoEquityLikeStats() {
        Etf gld = EtfBuilder.build(Resolver.resolve(InstrumentFixtures.payload("GLD", true), EtfSpecs.SNAPSHOT), NOW);
        assertThat(gld.equityLikeStats()).isEmpty();
        assertThat(gld.trailingPE()).isEmpty();
    }

    @Test
    void mutualFundHasNoSessionButGuaranteedFundMetrics() {
        var r = Resolver.resolve(InstrumentFixtures.payload("VFIAX", false), MutualFundSpecs.SNAPSHOT);
        assertThat(r.missingRequired()).isEmpty();
        MutualFund vfiax = MutualFundBuilder.build(r, NOW);
        assertThat(vfiax).isNotInstanceOf(IntradayTraded.class);
        assertThat(vfiax.expenseRatio()).isPositive();
        assertThat(vfiax.netAssets()).isPositive();
        assertThat(vfiax.yield()).isLessThan(java.math.BigDecimal.ONE);   // a fraction
        assertThat(vfiax.threeMonthReturn()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*FundBuildersTest'` → compilation FAILS.

- [ ] **Step 3: Write the implementation**

Move `TrailingDividend` to `instrument/TrailingDividend.java` (top level, same shape) and update `Equity` and `EquityBuilder` to use it.

`instrument/Fund.java`:
```java
/** ETFs and mutual funds: what both guarantee at snapshot depth. Detail-tier fund data is in {@code detail.FundDetail}. */
public sealed interface Fund permits Etf, MutualFund {
    BigDecimal ytdReturn();
    BigDecimal threeMonthReturn();
}
```
Update `Instrument permits Equity, Etf, MutualFund, Unclassified`, `IntradayTraded permits Equity, Etf`, `Quoted permits Equity, Etf`.

`instrument/Etf.java` and `MutualFund.java` as in Interfaces above, each with `assetClass()` returning `ETF` / `MUTUAL_FUND` and Javadoc noting the survey numbers (`expenseRatio` 89 %: UCITS lack it).

`assembly/specs/EtfSpecs.java`:
```java
public final class EtfSpecs {
    private EtfSpecs() {}
    private static final List<FieldSpec> OWN = List.of(
            required("ytdReturn", RAW, "v7:ytdReturn", "qs:defaultKeyStatistics.ytdReturn", "qs:fundPerformance.trailingReturns.ytd"),
            required("threeMonthReturn", RAW, "v7:trailingThreeMonthReturns", "qs:fundPerformance.trailingReturns.threeMonth"),
            optional("netAssets", RAW, "v7:netAssets", "qs:defaultKeyStatistics.totalAssets", "qs:summaryDetail.totalAssets"),
            optional("expenseRatio", RAW, "v7:netExpenseRatio|PERCENT", "qs:fundProfile.feesExpensesInvestment.annualReportExpenseRatio"),
            optional("yield", RAW, "v7:dividendYield|PERCENT", "qs:summaryDetail.yield", "qs:defaultKeyStatistics.yield"),
            optional("navPrice", RAW, "qs:summaryDetail.navPrice"),
            optional("beta3Year", RAW, "qs:defaultKeyStatistics.beta3Year"),
            optional("trailingThreeMonthNavReturns", RAW, "v7:trailingThreeMonthNavReturns"),
            optional("trailingPE", RAW, "v7:trailingPE", "qs:summaryDetail.trailingPE"),
            clustered("equityLikeStats", "equityLikeStats.bookValue", RAW, "v7:bookValue"),
            clustered("equityLikeStats", "equityLikeStats.priceToBook", RAW, "v7:priceToBook"),
            clustered("equityLikeStats", "equityLikeStats.sharesOutstanding", RAW, "v7:sharesOutstanding"),
            clustered("equityLikeStats", "equityLikeStats.financialCurrency", RAW, "v7:financialCurrency"),
            clustered("trailingDividend", "trailingDividend.rate", RAW, "v7:trailingAnnualDividendRate"),
            clustered("trailingDividend", "trailingDividend.yield", RAW, "v7:trailingAnnualDividendYield"));
    public static final List<FieldSpec> SNAPSHOT = Specs.concat(CoreSpecs.CORE, SessionSpecs.SESSION, BookSpecs.BOOK, PostMarketSpecs.POST_MARKET, OWN);
}
```
**Unit check required before committing (appendix "verify at impl"):** open `v7_SPY.json` and `qs_SPY.json`; if v7 `netExpenseRatio` is e.g. `0.0945` and `annualReportExpenseRatio` is `0.000945`, v7 is the percent and the `|PERCENT` override above is right; if both are the same magnitude, drop the override. Same check for `dividendYield` vs `summaryDetail.yield`. Write the finding into the appendix note in Task 18.

`assembly/specs/MutualFundSpecs.java`:
```java
public final class MutualFundSpecs {
    private MutualFundSpecs() {}
    private static final List<FieldSpec> OWN = List.of(
            required("netAssets", RAW, "v7:netAssets", "qs:defaultKeyStatistics.totalAssets", "qs:summaryDetail.totalAssets"),
            required("expenseRatio", RAW, "v7:netExpenseRatio|PERCENT", "qs:fundProfile.feesExpensesInvestment.annualReportExpenseRatio", "qs:defaultKeyStatistics.annualReportExpenseRatio"),
            required("yield", RAW, "v7:dividendYield|PERCENT", "qs:summaryDetail.yield"),
            required("dividendRate", RAW, "v7:dividendRate"),
            required("ytdReturn", RAW, "v7:ytdReturn", "qs:summaryDetail.ytdReturn", "qs:fundPerformance.trailingReturns.ytd"),
            required("threeMonthReturn", RAW, "v7:trailingThreeMonthReturns", "qs:fundPerformance.trailingReturns.threeMonth"),
            clustered("equityLikeStats", "equityLikeStats.bookValue", RAW, "v7:bookValue"),
            clustered("equityLikeStats", "equityLikeStats.priceToBook", RAW, "v7:priceToBook"),
            clustered("equityLikeStats", "equityLikeStats.sharesOutstanding", RAW, "v7:sharesOutstanding"),
            clustered("equityLikeStats", "equityLikeStats.financialCurrency", RAW, "v7:financialCurrency"),
            optional("trailingPE", RAW, "v7:trailingPE"),
            clustered("trailingDividend", "trailingDividend.rate", RAW, "v7:trailingAnnualDividendRate"),
            clustered("trailingDividend", "trailingDividend.yield", RAW, "v7:trailingAnnualDividendYield"));
    public static final List<FieldSpec> SNAPSHOT = Specs.concat(CoreSpecs.CORE, OWN);   // no Session, no Book
}
```

Builders follow `EquityBuilder` exactly: `EtfBuilder.build(r, fetchedAt)` = `new Etf(CoreBuilder.build(r), TierBuilders.session(r), TierBuilders.book(r), r.decimal("ytdReturn"), r.decimal("threeMonthReturn"), r.optDecimal("netAssets"), r.optDecimal("expenseRatio"), r.optDecimal("yield"), r.optDecimal("navPrice"), r.optDecimal("beta3Year"), r.optDecimal("trailingThreeMonthNavReturns"), r.optDecimal("trailingPE"), equityLikeStats(r), trailingDividend(r), TierBuilders.postMarket(r), fetchedAt)`; put `equityLikeStats(Resolved)` and `trailingDividend(Resolved)` in `TierBuilders` (cluster checks as in Task 6). `MutualFundBuilder.build` analogous without session/book/postMarket.

- [ ] **Step 4: Run the tests**

Run: `./gradlew spotlessApply test --tests '*FundBuildersTest' --tests '*EquityBuilderTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "Fund snapshots: Fund tier, Etf and MutualFund records, specs and builders" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: `Index`, `FxPair`, `Crypto`, `Future` and `SnapshotSpecs`

**Files:**
- Create: `instrument/Index.java`, `FxPair.java`, `Crypto.java`, `Future.java`; `assembly/specs/CryptoSpecs.java`, `FutureSpecs.java`, `SimpleSpecs.java`, `SnapshotSpecs.java`; `assembly/build/SimpleBuilders.java`, `CryptoBuilder.java`, `FutureBuilder.java`
- Modify: permits lists of `Instrument`, `IntradayTraded`, `Quoted`
- Test: `src/test/java/io/github/dimazigel/yfinance/assembly/build/OtherClassBuildersTest.java`

**Interfaces:**
- Produces: `record Index(Core core, Session session, Optional<TopOfBook> book, Instant fetchedAt)`, `record FxPair(...)` same shape; `record Crypto(Core core, Session session, BigDecimal marketCap, Supply supply, BigDecimal volume24Hr, BigDecimal volumeAllCurrencies, String fromCurrency, QuoteCurrency toCurrency, LocalDate startDate, String lastMarket, Branding branding, Instant fetchedAt) implements Instrument, IntradayTraded` with `record Supply(BigDecimal circulating, BigDecimal total, BigDecimal max)`, `record Branding(URI image, URI logo, URI coinMarketCap)`; `record Future(Core core, Session session, Optional<TopOfBook> book, Contract contract, Instant fetchedAt) implements Instrument, IntradayTraded, Quoted` with `record Contract(boolean isSpecificContract, LocalDate expireDate, long openInterest, Symbol underlyingSymbol, String underlyingExchangeSymbol, Symbol headSymbol)`; `SnapshotSpecs.forClass(AssetClass): List<FieldSpec>` (`UNCLASSIFIED` → `CoreSpecs.CORE`); `SnapshotSpecs.FALLBACK_MODULES = List.of("price", "summaryDetail", "quoteType")`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.SnapshotSpecs;
import io.github.dimazigel.yfinance.instrument.*;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OtherClassBuildersTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Test
    void indexAndFxHaveSessionAndBookOnly() {
        var idx = Resolver.resolve(InstrumentFixtures.payload("^GSPC", false), SnapshotSpecs.forClass(AssetClass.INDEX));
        assertThat(idx.missingRequired()).isEmpty();
        Index gspc = SimpleBuilders.index(idx, NOW);
        assertThat(gspc.book()).isPresent();
        assertThat(gspc.session().volume()).isPositive();

        var fx = Resolver.resolve(InstrumentFixtures.payload("EURUSD=X", false), SnapshotSpecs.forClass(AssetClass.FX));
        assertThat(fx.missingRequired()).isEmpty();
        FxPair eurusd = SimpleBuilders.fxPair(fx, NOW);
        assertThat(eurusd.core().currency().code()).isEqualTo("USD");
    }

    @Test
    void cryptoHasSupplyAndNoBook() {
        var r = Resolver.resolve(InstrumentFixtures.payload("BTC-USD", false), SnapshotSpecs.forClass(AssetClass.CRYPTO));
        assertThat(r.missingRequired()).isEmpty();
        Crypto btc = CryptoBuilder.build(r, NOW);
        assertThat(btc).isNotInstanceOf(Quoted.class);
        assertThat(btc.supply().circulating()).isPositive();
        assertThat(btc.supply().max()).isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO); // 0 for uncapped coins, kept raw
        assertThat(btc.fromCurrency()).isEqualTo("BTC");
        assertThat(btc.toCurrency().code()).isEqualTo("USD");
        assertThat(btc.branding().coinMarketCap().getHost()).contains("coinmarketcap");
    }

    @Test
    void futureHasContractAndNoLongName() {
        var r = Resolver.resolve(InstrumentFixtures.payload("ES=F", false), SnapshotSpecs.forClass(AssetClass.FUTURE));
        assertThat(r.missingRequired()).isEmpty();
        Future es = FutureBuilder.build(r, NOW);
        assertThat(es.core().longName()).isEmpty();
        assertThat(es.contract().expireDate()).isAfter(java.time.LocalDate.of(2020, 1, 1));
        assertThat(es.contract().underlyingSymbol()).isEqualTo(Symbol.of("ES=F"));
        assertThat(es.contract().openInterest()).isPositive();
        assertThat(es.book()).isPresent();
    }

    @Test
    void snapshotSpecsCoverEveryClass() {
        for (AssetClass c : AssetClass.values()) {
            assertThat(SnapshotSpecs.forClass(c)).as(c.name()).isNotEmpty();
        }
        assertThat(SnapshotSpecs.forClass(AssetClass.MUTUAL_FUND)).extracting(s -> s.name()).doesNotContain("open");
        assertThat(SnapshotSpecs.FALLBACK_MODULES).containsExactly("price", "summaryDetail", "quoteType");
    }
}
```

- [ ] **Step 2: Run test to verify it fails** → compilation FAILS.

- [ ] **Step 3: Write the implementation**

Records as in Interfaces (each `assetClass()` returns its constant). Permits: `Instrument permits Equity, Etf, MutualFund, Index, Crypto, FxPair, Future, Unclassified`; `IntradayTraded permits Equity, Etf, Index, Crypto, FxPair, Future`; `Quoted permits Equity, Etf, Index, FxPair, Future`.

`SimpleSpecs.SIMPLE = Specs.concat(CoreSpecs.CORE, SessionSpecs.SESSION, BookSpecs.BOOK)` (Index, FxPair).

`CryptoSpecs.SNAPSHOT = concat(CORE, SESSION, OWN)` with OWN:
```java
required("marketCap", RAW, "v7:marketCap", "qs:summaryDetail.marketCap"),
required("supply.circulating", RAW, "v7:circulatingSupply", "qs:summaryDetail.circulatingSupply"),
required("supply.total", RAW, "v7:totalSupply", "qs:summaryDetail.totalSupply"),
required("supply.max", RAW, "v7:maxSupply", "qs:summaryDetail.maxSupply"),
required("volume24Hr", RAW, "v7:volume24Hr", "qs:summaryDetail.volume24Hr"),
required("volumeAllCurrencies", RAW, "v7:volumeAllCurrencies", "qs:summaryDetail.volumeAllCurrencies"),
required("fromCurrency", RAW, "v7:fromCurrency", "qs:summaryDetail.fromCurrency"),
required("toCurrency", RAW, "v7:toCurrency", "qs:summaryDetail.toCurrency"),
required("startDate", EPOCH_DATE, "v7:startDate", "qs:summaryDetail.startDate"),
required("lastMarket", RAW, "v7:lastMarket", "qs:summaryDetail.lastMarket"),
required("branding.image", RAW, "v7:coinImageUrl"),
required("branding.logo", RAW, "v7:logoUrl"),
required("branding.coinMarketCap", RAW, "v7:coinMarketCapLink", "qs:summaryDetail.coinMarketCapLink")
```
`FutureSpecs.SNAPSHOT = concat(CORE, SESSION, BOOK, OWN)` with OWN:
```java
required("contract.contractSymbol", RAW, "v7:contractSymbol"),
required("contract.expireDate", EPOCH_DATE, "v7:expireDate"),
required("contract.openInterest", RAW, "v7:openInterest"),
required("contract.underlyingSymbol", RAW, "v7:underlyingSymbol"),
required("contract.underlyingExchangeSymbol", RAW, "v7:underlyingExchangeSymbol"),
required("contract.headSymbol", RAW, "v7:headSymbolAsString")
```
`SnapshotSpecs`:
```java
public final class SnapshotSpecs {
    private SnapshotSpecs() {}
    public static final List<String> FALLBACK_MODULES = List.of("price", "summaryDetail", "quoteType");
    public static List<FieldSpec> forClass(AssetClass assetClass) {
        return switch (assetClass) {
            case EQUITY -> EquitySpecs.SNAPSHOT;
            case ETF -> EtfSpecs.SNAPSHOT;
            case MUTUAL_FUND -> MutualFundSpecs.SNAPSHOT;
            case INDEX, FX -> SimpleSpecs.SIMPLE;
            case CRYPTO -> CryptoSpecs.SNAPSHOT;
            case FUTURE -> FutureSpecs.SNAPSHOT;
            case UNCLASSIFIED -> CoreSpecs.CORE;
        };
    }
}
```
Builders: `SimpleBuilders.index/fxPair` = `new Index(CoreBuilder.build(r), TierBuilders.session(r), TierBuilders.book(r), fetchedAt)`; `CryptoBuilder` maps the fields above (`URI.create` for branding, `QuoteCurrency.of` for `toCurrency`, `r.date("startDate")`); `FutureBuilder` maps `Contract` (`Symbol.of` for the two symbols, `r.bool("contract.contractSymbol")`).

- [ ] **Step 4: Run the tests** → `./gradlew spotlessApply test --tests '*BuildersTest' --tests '*BuilderTest'` PASS.

- [ ] **Step 5: Commit** — `"Index, FxPair, Crypto, Future snapshots and SnapshotSpecs.forClass"`.

---

### Task 9: `InstrumentService` — classify, resolve, fall back, build or downgrade; `Batch` semantics

**Files:**
- Create: `src/main/java/io/github/dimazigel/yfinance/service/InstrumentService.java`
- Test: `src/test/java/io/github/dimazigel/yfinance/service/InstrumentServiceTest.java`

**Interfaces:**
- Consumes: `RawQuoteClient`, `SnapshotSpecs`, builders, `Batch`/`Outcome`, `LogContext`.
- Produces: `InstrumentService(RawQuoteClient client, Clock clock)`; `Batch<Instrument> instruments(List<Symbol>)`; `<I extends Instrument> Batch<I> instruments(List<Symbol>, Class<I>)`; `Instrument instrument(Symbol)` (throws `YFDataException` for `UNKNOWN_SYMBOL`).

Algorithm (design §6): rows = `client.quoteRows(symbols)`; for each requested symbol in input order: no row → `Skipped(UNKNOWN_SYMBOL, "not in Yahoo's quote response")`; class = `AssetClass.fromQuoteType(row.quoteType)`; resolve `SnapshotSpecs.forClass(class or UNCLASSIFIED)` against `Payload(symbol, row, {})`; if `missingRequired` non-empty → fetch `client.modules(symbol, FALLBACK_MODULES)` once, merge, resolve again; then: core incomplete → `Skipped(UNKNOWN_SYMBOL, "core incomplete: …")` (dead quotes such as `quoteType NONE`); class present and nothing missing → build; class present and something missing → `Ok(Unclassified(core, reported, Optional.of(class), missing, empty, now))` + DEBUG log; class absent → `Ok(Unclassified(core, reported, empty, [], empty, now))`. Typed overload: `Ok(value)` whose class ≠ `I` → `Skipped(WRONG_ASSET_CLASS, actual class name)`; `Ok(Unclassified)` → `Skipped(DOWNGRADED, missing.toString())`. Wrap everything after `quoteRows` per symbol in try/catch → `Failed`. Wrap the whole call in `LogContext.scope("instruments", symbols)` and log `Batch.summary()` at INFO.

- [ ] **Step 1: Write the failing test**

```java
package io.github.dimazigel.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.api.QuoteApi;
import io.github.dimazigel.yfinance.api.QuoteSummaryApi;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.http.RawQuoteClient;
import io.github.dimazigel.yfinance.instrument.*;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstrumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private MockWebServer server;
    private InstrumentService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        // v7: answer with the captured rows for whatever symbols were asked; quoteSummary: the captured file per symbol
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                var url = request.getRequestUrl();
                if (url.encodedPath().equals("/v7/finance/quote")) {
                    String[] symbols = url.queryParameter("symbols").split(",");
                    return new MockResponse().setResponseCode(200).setBody(InstrumentFixtures.v7Response(symbols));
                }
                String symbol = url.pathSegments().getLast();
                try {
                    String body = Fixtures.load("instruments/qs_" + InstrumentFixtures.safe(symbol) + ".json");
                    return new MockResponse().setResponseCode(body.contains("\"result\":null") ? 404 : 200).setBody(body);
                } catch (IllegalArgumentException noFixture) {
                    return new MockResponse().setResponseCode(404).setBody("{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"Quote not found for symbol: " + symbol + "\"}}}");
                }
            }
        });
        var client = new RawQuoteClient(Fixtures.api(server, QuoteApi.class), Fixtures.api(server, QuoteSummaryApi.class));
        service = new InstrumentService(client, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void classifiesEveryClassInOneBatchWithoutFallbackRequests() {
        var batch = service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("SPY"), Symbol.of("VFIAX"), Symbol.of("^GSPC"),
                Symbol.of("BTC-USD"), Symbol.of("EURUSD=X"), Symbol.of("ES=F")));

        assertThat(batch.values()).extracting(Instrument::assetClass).containsExactly(AssetClass.EQUITY, AssetClass.ETF,
                AssetClass.MUTUAL_FUND, AssetClass.INDEX, AssetClass.CRYPTO, AssetClass.FX, AssetClass.FUTURE);
        assertThat(batch.values()).allSatisfy(i -> assertThat(i.fetchedAt()).isEqualTo(NOW));
        assertThat(server.getRequestCount()).as("one v7 call, no quoteSummary").isEqualTo(1);
    }

    @Test
    void unknownSymbolIsSkippedNotFailed() {
        var batch = service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("NO_SUCH_SYMBOL_XYZ")));
        assertThat(batch.get(Symbol.of("NO_SUCH_SYMBOL_XYZ"))).containsInstanceOf(Outcome.Skipped.class)
                .get().satisfies(o -> assertThat(((Outcome.Skipped<Instrument>) o).reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL));
        assertThat(batch.failed()).isEmpty();
        assertThatThrownBy(() -> service.instrument(Symbol.of("NO_SUCH_SYMBOL_XYZ"))).isInstanceOf(YFDataException.class);
    }

    @Test
    void ucitsEtfTriggersExactlyOneFallbackRequestAndClassifies() {
        var batch = service.instruments(List.of(Symbol.of("CSPX.L"), Symbol.of("SPY")));
        assertThat(batch.values()).allSatisfy(i -> assertThat(i).isInstanceOf(Etf.class));
        assertThat(server.getRequestCount()).as("v7 + one quoteSummary for CSPX.L only").isEqualTo(2);
    }

    @Test
    void preferredShareIsDowngradedWithTheMissingFieldNamed() {
        Instrument bacpl = service.instrument(Symbol.of("BAC-PL"));
        assertThat(bacpl).isInstanceOfSatisfying(Unclassified.class, u -> {
            assertThat(u.attempted()).contains(AssetClass.EQUITY);
            assertThat(u.missing()).contains("marketCap");
            assertThat(u.core().price()).isPositive();   // the universal core is still there
        });
        assertThat(server.getRequestCount()).as("fallback was tried before downgrading").isEqualTo(2);
    }

    @Test
    void deadQuoteTypeNoneIsUnknown() {
        var batch = service.instruments(List.of(Symbol.of("RIDE")));
        assertThat(batch.skipped()).singleElement().satisfies(s -> assertThat(s.reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL));
    }

    @Test
    void typedOverloadSkipsOtherClassesAndDowngrades() {
        var equities = service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("SPY"), Symbol.of("BAC-PL")), Equity.class);
        assertThat(equities.values()).singleElement().satisfies(e -> assertThat(e.symbol()).isEqualTo(Symbol.of("AAPL")));
        assertThat(equities.skipped()).extracting(Outcome.Skipped::reason).containsExactly(SkipReason.WRONG_ASSET_CLASS, SkipReason.DOWNGRADED);
    }

    @Test
    void matchesRowsToRequestedSymbolsCaseInsensitively() {   // Review Focus 2
        var batch = service.instruments(List.of(Symbol.of("aapl")));
        assertThat(batch.values()).singleElement().satisfies(i -> assertThat(i.symbol()).isEqualTo(Symbol.of("AAPL")));
    }

    @Test
    void duplicatesYieldDuplicateOutcomes() {   // Review Focus 3
        var batch = service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("AAPL")));
        assertThat(batch.size()).isEqualTo(2);
        assertThat(batch.values()).hasSize(2);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void emptyInputMakesNoRequest() {   // Review Focus 3
        assertThat(service.instruments(List.of()).size()).isZero();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void transportFailureOnFallbackIsFailedNotThrown() throws Exception {
        server.shutdown();   // v7 itself fails: every symbol Failed, nothing thrown
        var batch = service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("SPY")));
        assertThat(batch.failed()).hasSize(2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails** → compilation FAILS (`InstrumentService`).

- [ ] **Step 3: Write the implementation**

```java
package io.github.dimazigel.yfinance.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.build.*;
import io.github.dimazigel.yfinance.assembly.specs.CoreSpecs;
import io.github.dimazigel.yfinance.assembly.specs.SnapshotSpecs;
import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.http.RawQuoteClient;
import io.github.dimazigel.yfinance.instrument.*;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Snapshot-depth instruments: one batched v7 request, per-field quoteSummary fallback only when a guarantee is short. */
public final class InstrumentService {

    private static final Logger LOG = LoggerFactory.getLogger(InstrumentService.class);

    private final RawQuoteClient client;
    private final Clock clock;

    public InstrumentService(RawQuoteClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public Batch<Instrument> instruments(List<Symbol> symbols) {
        if (symbols.isEmpty()) {
            return new Batch<>(List.of());
        }
        try (var ignored = LogContext.scope("instruments", symbols)) {
            Instant now = clock.instant();
            Map<Symbol, JsonNode> rows;
            try {
                rows = client.quoteRows(symbols);
            } catch (YFinanceException e) {
                return new Batch<>(symbols.stream().<Outcome<Instrument>>map(s -> Outcome.failed(s, e)).toList());
            }
            var outcomes = new ArrayList<Outcome<Instrument>>(symbols.size());
            for (Symbol symbol : symbols) {
                JsonNode row = rows.get(symbol);
                if (row == null) {
                    outcomes.add(Outcome.skipped(symbol, SkipReason.UNKNOWN_SYMBOL, "not in Yahoo's quote response"));
                    continue;
                }
                try {
                    outcomes.add(assemble(symbol, row, now));
                } catch (YFinanceException e) {
                    outcomes.add(Outcome.failed(symbol, e));
                } catch (RuntimeException e) {
                    outcomes.add(Outcome.failed(symbol, new YFDataException("Failed to assemble " + symbol, e)));
                }
            }
            var batch = new Batch<>(outcomes);
            LOG.atInfo().log("instruments: {}", batch.summary());
            return batch;
        }
    }

    public <I extends Instrument> Batch<I> instruments(List<Symbol> symbols, Class<I> as) {
        var narrowed = new ArrayList<Outcome<I>>();
        for (Outcome<Instrument> o : instruments(symbols).outcomes()) {
            narrowed.add(switch (o) {
                case Outcome.Ok<Instrument> ok when as.isInstance(ok.value()) -> Outcome.ok(ok.symbol(), as.cast(ok.value()));
                case Outcome.Ok<Instrument> ok when ok.value() instanceof Unclassified u ->
                        Outcome.skipped(ok.symbol(), SkipReason.DOWNGRADED, "missing " + u.missing() + " (reported " + u.reportedQuoteType() + ")");
                case Outcome.Ok<Instrument> ok -> Outcome.skipped(ok.symbol(), SkipReason.WRONG_ASSET_CLASS, ok.value().assetClass().name());
                case Outcome.Skipped<Instrument> s -> Outcome.skipped(s.symbol(), s.reason(), s.detail());
                case Outcome.Failed<Instrument> f -> Outcome.failed(f.symbol(), f.error());
            });
        }
        return new Batch<>(narrowed);
    }

    public Instrument instrument(Symbol symbol) {
        return instruments(List.of(symbol)).outcomes().getFirst().orElseThrow();
    }

    private Outcome<Instrument> assemble(Symbol symbol, JsonNode row, Instant now) {
        String reported = row.path("quoteType").asText("");
        Optional<AssetClass> attempted = AssetClass.fromQuoteType(reported);
        AssetClass target = attempted.orElse(AssetClass.UNCLASSIFIED);
        var specs = SnapshotSpecs.forClass(target);
        var payload = new Payload(symbol, Optional.of(row), Map.of());
        Resolved resolved = Resolver.resolve(payload, specs);
        if (!resolved.missingRequired().isEmpty()) {
            Optional<Map<String, JsonNode>> modules = client.modules(symbol, SnapshotSpecs.FALLBACK_MODULES);
            if (modules.isPresent()) {
                payload = payload.withModules(modules.get());
                resolved = Resolver.resolve(payload, specs);
            }
        }
        Resolved coreOnly = Resolver.resolve(payload, CoreSpecs.CORE);
        if (!coreOnly.missingRequired().isEmpty()) {
            return Outcome.skipped(symbol, SkipReason.UNKNOWN_SYMBOL, "core incomplete: " + coreOnly.missingRequired());
        }
        if (attempted.isPresent() && resolved.missingRequired().isEmpty()) {
            return Outcome.ok(symbol, build(target, resolved, now));
        }
        Core core = CoreBuilder.build(coreOnly);
        List<String> missing = attempted.isPresent() ? resolved.missingRequired() : List.of();
        if (attempted.isPresent()) {
            LOG.atDebug().addKeyValue("missing", missing).log("{} downgraded from {}: missing {}", symbol, target, missing);
        }
        return Outcome.ok(symbol, new Unclassified(core, reported, attempted, missing, Optional.empty(), now));
    }

    private static Instrument build(AssetClass target, Resolved r, Instant now) {
        return switch (target) {
            case EQUITY -> EquityBuilder.build(r, now);
            case ETF -> EtfBuilder.build(r, now);
            case MUTUAL_FUND -> MutualFundBuilder.build(r, now);
            case INDEX -> SimpleBuilders.index(r, now);
            case FX -> SimpleBuilders.fxPair(r, now);
            case CRYPTO -> CryptoBuilder.build(r, now);
            case FUTURE -> FutureBuilder.build(r, now);
            case UNCLASSIFIED -> throw new IllegalStateException("UNCLASSIFIED is built as Unclassified, not here");
        };
    }
}
```
`LogContext.scope(String, List<Symbol>)` exists (Task: logging PR). For the per-symbol `Failed` on a transport error during fallback, `client.modules` throws `YFinanceException`, which the loop catches.

- [ ] **Step 4: Run the tests** → `./gradlew spotlessApply test --tests '*InstrumentServiceTest'` PASS (10 tests). Then `./gradlew build` — everything green with the old model still present.

- [ ] **Step 5: Commit** — `"InstrumentService: classify, resolve, fall back, build or downgrade; Batch semantics"`.

---

## Phase 2 — Detail tier

Yahoo's list-shaped module data has three shapes the row mappers must know: plain arrays of objects (`topHoldings.holdings: [{symbol, holdingName, holdingPercent}]`, `recommendationTrend.trend`, `institutionOwnership.ownershipList`), arrays of **single-key objects** (`sectorWeightings: [{"realestate": 0.03}, {"technology": 0.31}]`, `bondRatings: [{"bb": 0.1}]`), and keyed objects (`trailingReturns: {ytd, oneMonth, …, asOfDate}`). All detail records live in `detail/`; leaf optionality is `Optional`, never `@Nullable`.

### Task 10: Fund detail — `FundDetail` shared records, `EtfDetail`, `MutualFundDetail`, specs, builders

**Files:**
- Create: `detail/package-info.java`, `detail/FundDetail.java`, `detail/EtfDetail.java`, `detail/MutualFundDetail.java`; `assembly/specs/FundDetailSpecs.java`, `EtfDetailSpecs.java`, `MutualFundDetailSpecs.java`, `DetailSpecs.java`; `assembly/build/Nodes.java`, `FundDetailBuilder.java`
- Test: `src/test/java/io/github/dimazigel/yfinance/assembly/build/FundDetailBuilderTest.java`

**Interfaces:**
- Produces:
  - `sealed interface FundDetail permits EtfDetail, MutualFundDetail { Symbol symbol(); String family(); LocalDate inceptionDate(); TrailingReturns trailingReturns(); List<YearReturn> annualTotalReturns(); Allocation allocation(); EquityValuation equityValuation(); List<Holding> holdings(); List<SectorWeight> sectorWeightings(); List<BondRating> bondRatings(); Optional<String> category(); Instant fetchedAt(); }` with nested `record TrailingReturns(BigDecimal ytd, BigDecimal oneMonth, BigDecimal threeMonth, BigDecimal oneYear, BigDecimal threeYear, BigDecimal fiveYear, BigDecimal tenYear, LocalDate asOf)`, `record YearReturn(int year, BigDecimal value)`, `record Allocation(BigDecimal stock, BigDecimal bond, BigDecimal cash, BigDecimal preferred, BigDecimal convertible, BigDecimal other)`, `record EquityValuation(BigDecimal priceToEarnings, BigDecimal priceToBook, BigDecimal priceToSales, BigDecimal priceToCashflow)`, `record Holding(String symbol, String name, BigDecimal weight)`, `record SectorWeight(String sector, BigDecimal weight)`, `record BondRating(String rating, BigDecimal weight)`.
  - `record EtfDetail(Symbol symbol, String family, String legalType, LocalDate inceptionDate, TrailingReturns trailingReturns, List<YearReturn> annualTotalReturns, Allocation allocation, EquityValuation equityValuation, List<Holding> holdings, List<SectorWeight> sectorWeightings, List<BondRating> bondRatings, Optional<String> category, Optional<BigDecimal> beta3Year, Optional<String> longBusinessSummary, Optional<URI> styleBoxUrl, Instant fetchedAt) implements FundDetail`.
  - `record MutualFundDetail(Symbol symbol, String family, LocalDate inceptionDate, TrailingReturns trailingReturns, List<YearReturn> annualTotalReturns, Allocation allocation, EquityValuation equityValuation, List<Holding> holdings, List<SectorWeight> sectorWeightings, List<BondRating> bondRatings, Optional<String> category, String longBusinessSummary, Morningstar morningstar, BigDecimal annualHoldingsTurnover, BigDecimal lastCapGain, BigDecimal lastDividendValue, BigDecimal beta3Year, Minimums minimums, List<String> brokerages, LoadAdjustedReturns loadAdjustedReturns, RankInCategory rankInCategory, URI styleBoxUrl, Instant fetchedAt) implements FundDetail` with nested `record Morningstar(int overallRating, int riskRating)`, `record Minimums(BigDecimal initial, BigDecimal subsequent)`, `record LoadAdjustedReturns(BigDecimal oneYear, BigDecimal threeYear, BigDecimal fiveYear, BigDecimal tenYear)`, `record RankInCategory(BigDecimal ytd, BigDecimal oneMonth, BigDecimal threeMonth, BigDecimal oneYear, BigDecimal threeYear, BigDecimal fiveYear)`.
  - `DetailSpecs.modules(AssetClass): List<String>` — ETF/MUTUAL_FUND → `price, quoteType, summaryDetail, defaultKeyStatistics, assetProfile, fundProfile, topHoldings, fundPerformance`; EQUITY → `price, quoteType, summaryDetail, defaultKeyStatistics, financialData, assetProfile, calendarEvents, secFilings, recommendationTrend, upgradeDowngradeHistory, earningsTrend, earningsHistory, majorHoldersBreakdown, institutionOwnership, fundOwnership, insiderHolders, insiderTransactions, netSharePurchaseActivity`; CRYPTO → `price, quoteType, summaryDetail, assetProfile`; others → empty list (no detail tier).
  - `Nodes` (package-private helpers over `JsonNode`): `decimal(node, key): BigDecimal`, `optDecimal(node, key): Optional<BigDecimal>`, `optLong`, `optInt`, `optString`, `string(node, key)`, `instantSeconds(node, key)`, `optInstantSeconds`, `optDateSeconds`, `epochDate(node, key): LocalDate`, `text(node, key)`; each treats `null`/`""`/`{}` and `{raw: null}` as absent and unwraps `{raw, fmt}`.
  - `FundDetailBuilder.etf(Resolved, Symbol, Instant): EtfDetail`, `mutualFund(Resolved, Symbol, Instant): MutualFundDetail`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.DetailSpecs;
import io.github.dimazigel.yfinance.assembly.specs.EtfDetailSpecs;
import io.github.dimazigel.yfinance.assembly.specs.MutualFundDetailSpecs;
import io.github.dimazigel.yfinance.detail.EtfDetail;
import io.github.dimazigel.yfinance.detail.MutualFundDetail;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FundDetailBuilderTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    private static Payload detailPayload(String symbol) {
        return new Payload(Symbol.of(symbol), Optional.empty(), InstrumentFixtures.qsModules(symbol));
    }

    @Test
    void spyDetailHasEveryGuaranteeAndHoldings() {
        var r = Resolver.resolve(detailPayload("SPY"), EtfDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).isEmpty();
        EtfDetail spy = FundDetailBuilder.etf(r, Symbol.of("SPY"), NOW);
        assertThat(spy.family()).isNotBlank();
        assertThat(spy.legalType()).isNotBlank();
        assertThat(spy.inceptionDate()).isBefore(java.time.LocalDate.of(2000, 1, 1));
        assertThat(spy.trailingReturns().oneYear()).isNotNull();
        assertThat(spy.trailingReturns().asOf()).isAfter(java.time.LocalDate.of(2025, 1, 1));
        assertThat(spy.annualTotalReturns()).isNotEmpty().allSatisfy(y -> assertThat(y.year()).isBetween(1990, 2030));
        BigDecimal total = spy.allocation().stock().add(spy.allocation().bond()).add(spy.allocation().cash());
        assertThat(total).isBetween(new BigDecimal("0.9"), new BigDecimal("1.1"));   // fractions summing to ~1
        assertThat(spy.holdings()).isNotEmpty().allSatisfy(h -> {
            assertThat(h.symbol()).isNotBlank();
            assertThat(h.weight()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        });
        assertThat(spy.sectorWeightings()).isNotEmpty();
        assertThat(spy.equityValuation().priceToEarnings()).isPositive();
    }

    @Test
    void goldEtfHasNoHoldingsButIsStillComplete() {
        var r = Resolver.resolve(detailPayload("GLD"), EtfDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).isEmpty();
        EtfDetail gld = FundDetailBuilder.etf(r, Symbol.of("GLD"), NOW);
        assertThat(gld.holdings()).isEmpty();
        assertThat(gld.sectorWeightings()).isEmpty();
    }

    @Test
    void mutualFundDetailHasMorningstarAndMinimums() {
        var r = Resolver.resolve(detailPayload("VFIAX"), MutualFundDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).isEmpty();
        MutualFundDetail v = FundDetailBuilder.mutualFund(r, Symbol.of("VFIAX"), NOW);
        assertThat(v.morningstar().overallRating()).isBetween(1, 5);
        assertThat(v.minimums().initial()).isPositive();
        assertThat(v.brokerages()).isNotEmpty();
        assertThat(v.loadAdjustedReturns().oneYear()).isNotNull();
        assertThat(v.rankInCategory().ytd()).isNotNull();
        assertThat(v.styleBoxUrl().getHost()).isNotBlank();
    }

    @Test
    void moduleSetsPerClass() {
        assertThat(DetailSpecs.modules(AssetClass.ETF)).contains("fundProfile", "topHoldings", "fundPerformance");
        assertThat(DetailSpecs.modules(AssetClass.EQUITY)).contains("financialData", "earningsTrend", "insiderTransactions");
        assertThat(DetailSpecs.modules(AssetClass.INDEX)).isEmpty();
        assertThat(DetailSpecs.modules(AssetClass.FX)).isEmpty();
        assertThat(DetailSpecs.modules(AssetClass.FUTURE)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails** → compilation FAILS.

- [ ] **Step 3: Write the implementation**

`FundDetailSpecs.COMMON`:
```java
required("family", RAW, "qs:fundProfile.family", "qs:defaultKeyStatistics.fundFamily"),
required("inceptionDate", EPOCH_DATE, "qs:defaultKeyStatistics.fundInceptionDate"),
required("trailingReturns.ytd", RAW, "qs:fundPerformance.trailingReturns.ytd"),
required("trailingReturns.oneMonth", RAW, "qs:fundPerformance.trailingReturns.oneMonth"),
required("trailingReturns.threeMonth", RAW, "qs:fundPerformance.trailingReturns.threeMonth"),
required("trailingReturns.oneYear", RAW, "qs:fundPerformance.trailingReturns.oneYear"),
required("trailingReturns.threeYear", RAW, "qs:fundPerformance.trailingReturns.threeYear"),
required("trailingReturns.fiveYear", RAW, "qs:fundPerformance.trailingReturns.fiveYear"),
required("trailingReturns.tenYear", RAW, "qs:fundPerformance.trailingReturns.tenYear"),
required("trailingReturns.asOf", EPOCH_DATE, "qs:fundPerformance.trailingReturns.asOfDate"),
list("annualTotalReturns", "qs:fundPerformance.annualTotalReturns.returns"),
required("allocation.stock", RAW, "qs:topHoldings.stockPosition"),
required("allocation.bond", RAW, "qs:topHoldings.bondPosition"),
required("allocation.cash", RAW, "qs:topHoldings.cashPosition"),
required("allocation.preferred", RAW, "qs:topHoldings.preferredPosition"),
required("allocation.convertible", RAW, "qs:topHoldings.convertiblePosition"),
required("allocation.other", RAW, "qs:topHoldings.otherPosition"),
required("equityValuation.priceToEarnings", RAW, "qs:topHoldings.equityHoldings.priceToEarnings"),
required("equityValuation.priceToBook", RAW, "qs:topHoldings.equityHoldings.priceToBook"),
required("equityValuation.priceToSales", RAW, "qs:topHoldings.equityHoldings.priceToSales"),
required("equityValuation.priceToCashflow", RAW, "qs:topHoldings.equityHoldings.priceToCashflow"),
list("holdings", "qs:topHoldings.holdings"),
list("sectorWeightings", "qs:topHoldings.sectorWeightings"),
list("bondRatings", "qs:topHoldings.bondRatings"),
optional("category", RAW, "qs:fundProfile.categoryName", "qs:defaultKeyStatistics.category", "qs:fundPerformance.fundCategoryName")
```
`EtfDetailSpecs.DETAIL = concat(COMMON, List.of(required("legalType", RAW, "qs:fundProfile.legalType", "qs:defaultKeyStatistics.legalType"), optional("beta3Year", RAW, "qs:defaultKeyStatistics.beta3Year"), optional("longBusinessSummary", RAW, "qs:assetProfile.longBusinessSummary"), optional("styleBoxUrl", RAW, "qs:fundProfile.styleBoxUrl")))`.
`MutualFundDetailSpecs.DETAIL = concat(COMMON, List.of(required("longBusinessSummary", RAW, "qs:assetProfile.longBusinessSummary"), required("morningstar.overallRating", RAW, "qs:defaultKeyStatistics.morningStarOverallRating"), required("morningstar.riskRating", RAW, "qs:defaultKeyStatistics.morningStarRiskRating"), required("annualHoldingsTurnover", RAW, "qs:defaultKeyStatistics.annualHoldingsTurnover"), required("lastCapGain", RAW, "qs:defaultKeyStatistics.lastCapGain"), required("lastDividendValue", RAW, "qs:defaultKeyStatistics.lastDividendValue"), required("beta3Year", RAW, "qs:defaultKeyStatistics.beta3Year"), required("minimums.initial", RAW, "qs:fundProfile.initInvestment"), required("minimums.subsequent", RAW, "qs:fundProfile.subseqInvestment"), list("brokerages", "qs:fundProfile.brokerages"), required("loadAdjustedReturns.oneYear", RAW, "qs:fundPerformance.loadAdjustedReturns.oneYear"), … threeYear, fiveYear, tenYear …, required("rankInCategory.ytd", RAW, "qs:fundPerformance.rankInCategory.ytd"), … oneMonth, threeMonth, oneYear, threeYear, fiveYear …, required("styleBoxUrl", RAW, "qs:fundProfile.styleBoxUrl")))` — write every `…` row out in full in the source; they follow the pattern shown.

`Nodes.java` (helpers over `JsonNode`, unwrapping `{raw, fmt}` like `Payload.present`):
```java
final class Nodes {
    private Nodes() {}
    static Optional<JsonNode> get(JsonNode node, String key) {
        JsonNode v = node.path(key);
        if (v.isObject() && v.has("raw")) v = v.get("raw");
        if (v.isMissingNode() || v.isNull() || (v.isTextual() && v.asText().isBlank()) || ((v.isObject() || v.isArray()) && v.isEmpty())) return Optional.empty();
        return Optional.of(v);
    }
    static BigDecimal decimal(JsonNode node, String key) { return get(node, key).map(Nodes::toDecimal).orElseThrow(() -> new IllegalStateException("missing " + key)); }
    static Optional<BigDecimal> optDecimal(JsonNode node, String key) { return get(node, key).map(Nodes::toDecimal); }
    static Optional<Long> optLong(JsonNode node, String key) { return get(node, key).map(JsonNode::asLong); }
    static Optional<Integer> optInt(JsonNode node, String key) { return get(node, key).map(JsonNode::asInt); }
    static String string(JsonNode node, String key) { return get(node, key).map(JsonNode::asText).orElseThrow(() -> new IllegalStateException("missing " + key)); }
    static Optional<String> optString(JsonNode node, String key) { return get(node, key).map(JsonNode::asText); }
    static Optional<Instant> optInstantSeconds(JsonNode node, String key) { return get(node, key).map(v -> Instant.ofEpochSecond(v.asLong())); }
    static Optional<LocalDate> optDateSeconds(JsonNode node, String key) { return optInstantSeconds(node, key).map(i -> LocalDate.ofInstant(i, ZoneOffset.UTC)); }
    static Optional<URI> optUri(JsonNode node, String key) { return optString(node, key).flatMap(Nodes::uri); }
    static Optional<URI> uri(String s) { try { return Optional.of(new URI(s)); } catch (URISyntaxException e) { return Optional.empty(); } }
    private static BigDecimal toDecimal(JsonNode v) { return v.isNumber() ? v.decimalValue() : new BigDecimal(v.asText().strip()); }
    /** Yahoo's "[{key: value}, …]" lists: one map entry per element. */
    static List<Map.Entry<String, BigDecimal>> singleKeyList(List<JsonNode> nodes) {
        var out = new ArrayList<Map.Entry<String, BigDecimal>>();
        for (JsonNode n : nodes) {
            var it = n.fields();
            if (it.hasNext()) { var e = it.next(); get(n, e.getKey()).ifPresent(v -> out.add(Map.entry(e.getKey(), toDecimal(v)))); }
        }
        return List.copyOf(out);
    }
}
```

`FundDetailBuilder` — shared pieces:
```java
static FundDetail.TrailingReturns trailingReturns(Resolved r) {
    return new FundDetail.TrailingReturns(r.decimal("trailingReturns.ytd"), r.decimal("trailingReturns.oneMonth"), r.decimal("trailingReturns.threeMonth"),
            r.decimal("trailingReturns.oneYear"), r.decimal("trailingReturns.threeYear"), r.decimal("trailingReturns.fiveYear"), r.decimal("trailingReturns.tenYear"), r.date("trailingReturns.asOf"));
}
static List<FundDetail.YearReturn> annualReturns(Resolved r) {
    return r.list("annualTotalReturns").stream()
            .filter(n -> Nodes.get(n, "year").isPresent() && Nodes.get(n, "annualValue").isPresent())
            .map(n -> new FundDetail.YearReturn(Integer.parseInt(Nodes.string(n, "year")), Nodes.decimal(n, "annualValue"))).toList();
}
static FundDetail.Allocation allocation(Resolved r) { return new FundDetail.Allocation(r.decimal("allocation.stock"), r.decimal("allocation.bond"), r.decimal("allocation.cash"), r.decimal("allocation.preferred"), r.decimal("allocation.convertible"), r.decimal("allocation.other")); }
static FundDetail.EquityValuation equityValuation(Resolved r) { … four decimals … }
static List<FundDetail.Holding> holdings(Resolved r) {
    return r.list("holdings").stream().filter(n -> Nodes.get(n, "symbol").isPresent() && Nodes.get(n, "holdingPercent").isPresent())
            .map(n -> new FundDetail.Holding(Nodes.string(n, "symbol"), Nodes.optString(n, "holdingName").orElse(Nodes.string(n, "symbol")), Nodes.decimal(n, "holdingPercent"))).toList();
}
static List<FundDetail.SectorWeight> sectors(Resolved r) { return Nodes.singleKeyList(r.list("sectorWeightings")).stream().map(e -> new FundDetail.SectorWeight(e.getKey(), e.getValue())).toList(); }
static List<FundDetail.BondRating> bondRatings(Resolved r) { return Nodes.singleKeyList(r.list("bondRatings")).stream().map(e -> new FundDetail.BondRating(e.getKey(), e.getValue())).toList(); }
```
`etf(...)` and `mutualFund(...)` assemble their records from these plus their own required fields (`r.string("legalType")`, `r.intValue("morningstar.overallRating")`, `URI.create(r.string("styleBoxUrl"))`, `r.list("brokerages").stream().map(JsonNode::asText).toList()`, …).

`DetailSpecs.modules(AssetClass)` and `DetailSpecs.forClass(AssetClass): List<FieldSpec>` (ETF → `EtfDetailSpecs.DETAIL`, MUTUAL_FUND → `MutualFundDetailSpecs.DETAIL`, EQUITY/CRYPTO added in Tasks 11–12, others → empty).

- [ ] **Step 4: Run the tests** → PASS. If `allocation` fractions don't sum near 1 for SPY, check whether Yahoo reports them as fractions (they are, per the survey: `stockPosition: 0.99`); do not scale.

- [ ] **Step 5: Commit** — `"Fund detail: FundDetail shared records, EtfDetail, MutualFundDetail, specs and builders"`.

---

### Task 11: Equity detail — `EquityDetail` and its five sections

**Files:**
- Create: `detail/EquityDetail.java` (nested: `CompanyProfile`, `Officer`, `Governance`, `Statistics`, `FiscalCalendar`, `ShortInterest`, `LastSplit`, `LastDividend`, `FinancialHealth`, `Margins`, `Liquidity`, `AnalystView`, `Targets`, `Rating`, `Ownership`, `Breakdown`, `NetSharePurchaseActivity`), `detail/rows/RecommendationPeriod.java`, `EarningsHistoryEntry.java`, `PeriodEstimate.java`, `EpsTrendPeriod.java`, `EpsRevisionsPeriod.java`, `GrowthEstimate.java`, `UpgradeDowngrade.java`, `SecFiling.java`, `InstitutionalHolder.java`, `InsiderHolder.java`, `InsiderTransaction.java`; `assembly/specs/EquityDetailSpecs.java`; `assembly/build/EquityDetailBuilder.java`, `assembly/build/RowMappers.java`
- Modify: `assembly/specs/DetailSpecs.java` (EQUITY → `EquityDetailSpecs.DETAIL`)
- Test: `src/test/java/io/github/dimazigel/yfinance/assembly/build/EquityDetailBuilderTest.java`

**Interfaces:**
- `record EquityDetail(Symbol symbol, CompanyProfile profile, Statistics statistics, FinancialHealth financials, AnalystView analysts, Ownership ownership, Instant fetchedAt)`.
- `record CompanyProfile(String sector, String industry, String country, String city, String address1, String zip, URI website, String longBusinessSummary, List<Officer> officers, Optional<Integer> fullTimeEmployees, Optional<String> phone, Optional<String> state, Optional<URI> irWebsite, Optional<Governance> governance)`; `record Officer(String name, String title, Optional<Integer> age, Optional<Long> totalPay)`; `record Governance(int auditRisk, int boardRisk, int compensationRisk, int shareholderRightsRisk, int overallRisk)`.
- `record Statistics(long floatShares, BigDecimal heldPercentInsiders, BigDecimal heldPercentInstitutions, BigDecimal profitMargins, Optional<BigDecimal> beta, Optional<BigDecimal> enterpriseValue, Optional<BigDecimal> enterpriseToRevenue, Optional<BigDecimal> enterpriseToEbitda, Optional<FiscalCalendar> fiscal, Optional<BigDecimal> pegRatio, Optional<BigDecimal> payoutRatio, Optional<BigDecimal> priceToSales, Optional<BigDecimal> earningsQuarterlyGrowth, Optional<ShortInterest> shortInterest, Optional<LastSplit> lastSplit, Optional<LastDividend> lastDividend, Optional<LocalDate> exDividendDate, Optional<BigDecimal> fiveYearAvgDividendYield)`; `record FiscalCalendar(LocalDate lastFiscalYearEnd, LocalDate nextFiscalYearEnd, LocalDate mostRecentQuarter)`; `record ShortInterest(long sharesShort, BigDecimal shortRatio, LocalDate date, long sharesShortPriorMonth, BigDecimal percentSharesOut, Optional<BigDecimal> percentOfFloat)`; `record LastSplit(LocalDate date, String factor)`; `record LastDividend(BigDecimal value, LocalDate date)`.
- `record FinancialHealth(BigDecimal currentPrice, BigDecimal totalRevenue, BigDecimal revenuePerShare, BigDecimal grossProfits, Margins margins, BigDecimal totalCash, BigDecimal totalCashPerShare, BigDecimal totalDebt, Optional<BigDecimal> revenueGrowth, Optional<BigDecimal> debtToEquity, Optional<BigDecimal> ebitda, Optional<BigDecimal> freeCashflow, Optional<BigDecimal> operatingCashflow, Optional<BigDecimal> returnOnEquity, Optional<BigDecimal> returnOnAssets, Optional<Liquidity> liquidity, Optional<BigDecimal> earningsGrowth)`; `record Margins(BigDecimal gross, BigDecimal operating, BigDecimal ebitda)`; `record Liquidity(BigDecimal currentRatio, BigDecimal quickRatio)`.
- `record AnalystView(String recommendationKey, Optional<Targets> targets, Optional<Rating> rating, List<RecommendationPeriod> recommendationTrend, List<EarningsHistoryEntry> earningsHistory, List<PeriodEstimate> earningsEstimates, List<PeriodEstimate> revenueEstimates, List<EpsTrendPeriod> epsTrend, List<EpsRevisionsPeriod> epsRevisions, List<GrowthEstimate> growthEstimates, List<UpgradeDowngrade> upgradesDowngrades, List<SecFiling> secFilings)`; `record Targets(BigDecimal low, BigDecimal mean, BigDecimal median, BigDecimal high, int analystCount)`; `record Rating(BigDecimal mean, String averageAnalystRating)`.
- `record Ownership(Breakdown breakdown, List<InstitutionalHolder> institutions, List<InstitutionalHolder> funds, List<InsiderHolder> insiders, List<InsiderTransaction> insiderTransactions, NetSharePurchaseActivity netSharePurchaseActivity)`; `record Breakdown(BigDecimal insidersPercentHeld, BigDecimal institutionsPercentHeld, BigDecimal institutionsFloatPercentHeld, int institutionsCount)`; `record NetSharePurchaseActivity(String period, Optional<Integer> buyCount, Optional<Long> buyShares, Optional<Integer> sellCount, Optional<Long> sellShares, Optional<Integer> netCount, Optional<Long> netShares, Optional<Long> totalInsiderShares)`.
- Row records in `detail/rows/` keep the field sets of today's `model` records with `Optional` in place of `@Nullable` (e.g. `record RecommendationPeriod(String period, int strongBuy, int buy, int hold, int sell, int strongSell)`, `record PeriodEstimate(String period, Optional<LocalDate> endDate, Optional<BigDecimal> average, Optional<BigDecimal> low, Optional<BigDecimal> high, Optional<Integer> numberOfAnalysts, Optional<BigDecimal> yearAgo)`, `record InstitutionalHolder(Optional<LocalDate> reportDate, String organization, Optional<BigDecimal> pctHeld, Optional<Long> position, Optional<BigDecimal> value, Optional<BigDecimal> pctChange)`, …). A row lacking the one field that identifies it (`period`, `organization`, `filerName`, `firm`, `title`) is dropped and counted in a DEBUG line, per the drop-site logging convention.
- `EquityDetailSpecs.DETAIL`: every row of the appendix "Equity — detail" table, with these kinds: required as listed; `profile.governance.*` as cluster `governance` (5 rows); `statistics.fiscal.*` cluster `fiscal` (3); `statistics.shortInterest.*` cluster `shortInterest` (5 rows, `statistics.shortInterest.percentOfFloat` a plain optional); `statistics.lastSplit.*` cluster (2); `statistics.lastDividend.*` cluster (2); `financials.liquidity.*` cluster (2); `analysts.targets.*` cluster `targets` (5); `analysts.rating.*` cluster `rating` (2: `qs:financialData.recommendationMean`, `v7:averageAnalystRating` — the v7 row IS part of the payload when the detail is fetched through `Ticker`; when absent, the rating cluster is simply empty); lists as listed; `ownership.breakdown.*` required (4); `ownership.netSharePurchaseActivity` required with path `qs:netSharePurchaseActivity.period` (the object is read via the module node). Units: `statistics.fiscal.*`, `statistics.lastSplit.date`, `statistics.lastDividend.date`, `statistics.exDividendDate`, `statistics.shortInterest.date` are `EPOCH_DATE`; `statistics.fiveYearAvgDividendYield` and `financials.debtToEquity` are `PERCENT`; everything else `RAW`.
- `EquityDetailBuilder.build(Resolved r, Map<String, JsonNode> modules, Symbol, Instant): EquityDetail` — takes the modules too, because list rows and the net-share-purchase object are mapped from module nodes via `RowMappers`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.EquityDetailSpecs;
import io.github.dimazigel.yfinance.detail.EquityDetail;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EquityDetailBuilderTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    private static EquityDetail build(String symbol) {
        Map<String, JsonNode> modules = InstrumentFixtures.qsModules(symbol);
        var payload = new Payload(Symbol.of(symbol), Optional.of(InstrumentFixtures.v7Row(symbol)), modules);
        var r = Resolver.resolve(payload, EquityDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).as(symbol + " missing").isEmpty();
        return EquityDetailBuilder.build(r, modules, Symbol.of(symbol), NOW);
    }

    @Test
    void appleDetailIsComplete() {
        EquityDetail d = build("AAPL");
        assertThat(d.profile().sector()).isEqualTo("Technology");
        assertThat(d.profile().officers()).isNotEmpty().allSatisfy(o -> assertThat(o.name()).isNotBlank());
        assertThat(d.profile().governance()).isPresent();
        assertThat(d.statistics().floatShares()).isPositive();
        assertThat(d.statistics().heldPercentInstitutions()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(d.statistics().beta()).isPresent();
        assertThat(d.statistics().fiscal()).isPresent();
        assertThat(d.statistics().shortInterest()).isPresent();
        assertThat(d.financials().totalRevenue()).isPositive();
        assertThat(d.financials().margins().gross()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(d.financials().liquidity()).isPresent();
        assertThat(d.analysts().recommendationKey()).isNotBlank();
        assertThat(d.analysts().targets()).isPresent();
        assertThat(d.analysts().targets().get().low()).isLessThanOrEqualTo(d.analysts().targets().get().high());
        assertThat(d.analysts().rating()).isPresent();
        assertThat(d.analysts().recommendationTrend()).isNotEmpty();
        assertThat(d.analysts().earningsEstimates()).isNotEmpty();
        assertThat(d.analysts().epsTrend()).isNotEmpty();
        assertThat(d.ownership().breakdown().institutionsCount()).isPositive();
        assertThat(d.ownership().institutions()).isNotEmpty().allSatisfy(h -> assertThat(h.organization()).isNotBlank());
        assertThat(d.ownership().insiders()).isNotEmpty();
        assertThat(d.ownership().netSharePurchaseActivity().period()).isNotBlank();
    }

    @Test
    void lossMakingSmallCapHasEmptyOptionalsNotFailures() {
        EquityDetail plug = build("PLUG");
        assertThat(plug.statistics().pegRatio()).isEmpty();            // 84 % in survey; PLUG lacks it
        assertThat(plug.analysts().upgradesDowngrades()).isNotNull();    // possibly empty list, never absent
        assertThat(plug.analysts().secFilings()).isNotNull();
    }

    @Test
    void missingRequiredModuleIsReportedByFieldName() {
        var modules = new HashMap<>(InstrumentFixtures.qsModules("AAPL"));
        modules.remove("financialData");
        var r = Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.empty(), modules), EquityDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).contains("financials.totalRevenue", "analysts.recommendationKey");
    }

    @Test
    void rowsMissingTheirIdentifierAreDropped() throws Exception {
        var modules = new HashMap<>(InstrumentFixtures.qsModules("AAPL"));
        var trend = (com.fasterxml.jackson.databind.node.ObjectNode) modules.get("recommendationTrend").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) trend.get("trend").get(0)).remove("period");
        modules.put("recommendationTrend", trend);
        var r = Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.empty(), modules), EquityDetailSpecs.DETAIL);
        EquityDetail d = EquityDetailBuilder.build(r, modules, Symbol.of("AAPL"), NOW);
        assertThat(d.analysts().recommendationTrend()).hasSize(trend.get("trend").size() - 1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails** → compilation FAILS.

- [ ] **Step 3: Write the implementation** — records as in Interfaces; `EquityDetailSpecs.DETAIL` from the appendix (every row); `RowMappers` with one static method per list (`recommendationTrend(List<JsonNode>)`, `earningsHistory`, `estimates(List<JsonNode>, String key)` for `earningsEstimate`/`revenueEstimate`, `epsTrend`, `epsRevisions`, `growth`, `upgradesDowngrades`, `secFilings`, `holders`, `insiders`, `insiderTransactions`), each filtering rows whose identifier is absent and using `Nodes.*`; `netSharePurchaseActivity(JsonNode module)`. The builder reads clusters with `r.clusterPresent(...)` exactly as `EquityBuilder` does and the lists with `r.list(...)`. Register `EQUITY -> EquityDetailSpecs.DETAIL` in `DetailSpecs.forClass`.

- [ ] **Step 4: Run the tests** → PASS.

- [ ] **Step 5: Commit** — `"Equity detail: profile, statistics, financials, analyst view, ownership; specs and builders"`.

---

### Task 12: `CryptoDetail` and `DetailService`

**Files:**
- Create: `detail/CryptoDetail.java`; `assembly/specs/CryptoDetailSpecs.java`; `assembly/build/CryptoDetailBuilder.java`; `service/DetailService.java`
- Modify: `assembly/specs/DetailSpecs.java` (CRYPTO)
- Test: `src/test/java/io/github/dimazigel/yfinance/service/DetailServiceTest.java`

**Interfaces:**
- `record CryptoDetail(Symbol symbol, String name, String description, URI website, LocalDate startDate, BigDecimal fullyDilutedValue, Optional<URI> whitepaper, Optional<String> twitter, Optional<ProofOfWork> proofOfWork, Instant fetchedAt)` with `record ProofOfWork(long blockNumber, BigDecimal blockReward, BigDecimal netHashesPerSecond)`.
- `CryptoDetailSpecs.DETAIL`: `detail.name` R `qs:assetProfile.name`; `detail.description` R; `detail.website` R; `detail.startDate` R (`ISO_DATE`, `qs:assetProfile.startDate`); `detail.fullyDilutedValue` R `qs:summaryDetail.fullyDilutedValue`; `detail.whitepaper` O; `detail.twitter` O; `detail.proofOfWork.{blockNumber, blockReward, netHashesPerSecond}` cluster `proofOfWork`.
- `DetailService(RawQuoteClient client, Clock clock, int concurrency)`; `Outcome<EquityDetail> equity(Equity)`, `Batch<EquityDetail> equities(List<Equity>)`, and likewise `etf/etfs`, `mutualFund/mutualFunds`, `crypto/cryptos`. Per symbol: `client.modules(symbol, DetailSpecs.modules(class))` → empty → `Skipped(UNKNOWN_SYMBOL, "quoteSummary 404")`; resolve `DetailSpecs.forClass(class)` against `Payload(symbol, Optional.empty(), modules)` → missing → `Skipped(MODULE_ABSENT, String.join(",", missing))` (+ DEBUG log); else `Ok(build)`. Transport errors → `Failed`. Fan-out over a virtual-thread executor bounded by `concurrency` (copy the `Semaphore` pattern from `Tickers.fanOut`), results in input order. Wrapped in `LogContext.scope("details", symbols)`, INFO summary.

- [ ] **Step 1: Write the failing test**

```java
class DetailServiceTest {
    // MockWebServer dispatcher: quoteSummary path -> captured qs_<SYMBOL>.json, 404 envelope when no fixture (as in InstrumentServiceTest)
    // Build Equity/Etf/Crypto instances by running InstrumentService over the fixtures first (helper: instrumentOf("AAPL")).

    @Test void appleDetailIsOk()                       { assertThat(service.equity(aapl).orElseThrow().profile().sector()).isEqualTo("Technology"); }
    @Test void spyDetailIsOkAndCryptoDetailIsOk()      { assertThat(service.etf(spy).orElseThrow().legalType()).isNotBlank(); assertThat(service.crypto(btc).orElseThrow().name()).isEqualTo("Bitcoin"); }
    @Test void detailForVanishedSymbolIsSkippedNotFailed() {   // Review Focus 5
        // an Equity whose quoteSummary now 404s: build one from the AAPL v7 row but under symbol "GONE" (no qs fixture)
        Equity gone = withSymbol(aapl, "GONE");
        var outcome = service.equity(gone);
        assertThat(outcome).isInstanceOfSatisfying(Outcome.Skipped.class, s -> assertThat(s.reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL));
    }
    @Test void missingGuaranteedModuleIsSkippedWithFieldNames() {
        // dispatcher variant that strips financialData from the AAPL response
        var outcome = strippedService.equity(aapl);
        assertThat(outcome).isInstanceOfSatisfying(Outcome.Skipped.class, s -> { assertThat(s.reason()).isEqualTo(SkipReason.MODULE_ABSENT); assertThat(s.detail()).contains("financials.totalRevenue"); });
    }
    @Test void batchKeepsOrderAndIsolatesFailures() { /* [AAPL, GONE, PLUG] -> [Ok, Skipped, Ok]; request count == 3 */ }
    @Test void requestsOnlyTheClassModules() { /* takeRequest().queryParameter("modules") for SPY contains fundProfile and not financialData */ }
}
```
Write these out in full following `InstrumentServiceTest`'s dispatcher; `withSymbol(Equity, String)` copies the record with a `Core` whose `symbol` differs (use the record's canonical constructor with `core` replaced via `new Core(Symbol.of("GONE"), core.shortName(), …)`).

- [ ] **Step 2: Run test to verify it fails** → compilation FAILS.

- [ ] **Step 3: Write the implementation** — `CryptoDetail`, `CryptoDetailSpecs`, `CryptoDetailBuilder` (ISO date for `startDate`; `URI.create`), `DetailService` per the interface above. The generic core:
```java
private <D> Batch<D> fanOut(List<? extends Instrument> instruments, AssetClass expected, BiFunction<Resolved, Map<String, JsonNode>, D> build) { … }
private <D> Outcome<D> one(Instrument instrument, AssetClass expected, BiFunction<Resolved, Map<String, JsonNode>, D> build) {
    Symbol symbol = instrument.symbol();
    Optional<Map<String, JsonNode>> modules = client.modules(symbol, DetailSpecs.modules(expected));
    if (modules.isEmpty()) return Outcome.skipped(symbol, SkipReason.UNKNOWN_SYMBOL, "quoteSummary 404");
    Resolved r = Resolver.resolve(new Payload(symbol, Optional.empty(), modules.get()), DetailSpecs.forClass(expected));
    if (!r.missingRequired().isEmpty()) {
        LOG.atDebug().addKeyValue("missing", r.missingRequired()).log("{} detail skipped: missing {}", symbol, r.missingRequired());
        return Outcome.skipped(symbol, SkipReason.MODULE_ABSENT, String.join(",", r.missingRequired()));
    }
    return Outcome.ok(symbol, build.apply(r, modules.get()));
}
```
For equities include the v7 row in the payload when the caller has it: `equity(Equity e)` cannot reconstruct the raw row, so the `analysts.rating` cluster relies on `qs:financialData.recommendationMean` + the v7 path being absent → the builder must treat `rating` as present when `recommendationMean` is present and use `Optional<String>` for `averageAnalystRating` inside `Rating`: change `Rating` to `record Rating(BigDecimal mean, Optional<String> averageAnalystRating)` and make `analysts.rating.averageAnalystRating` an `optional(...)` spec (appendix note in Task 18).

- [ ] **Step 4: Run the tests** → `./gradlew spotlessApply test` PASS; `./gradlew build` green.

- [ ] **Step 5: Commit** — `"CryptoDetail and DetailService: per-class module sets, MODULE_ABSENT/UNKNOWN_SYMBOL skips, bounded fan-out"`.

---

## Phase 3 — Market and fundamentals

### Task 13: `market` package — re-typed `PriceBar`, `HistoryMetadata`, corporate actions

**Files:**
- Move (git mv) `model/PriceHistory.java`, `PriceBar.java`, `HistoryMetadata.java`, `Dividend.java`, `Split.java`, `CapitalGain.java`, `mapper/PriceBarResampler.java` → `market/`; create `market/package-info.java`
- Modify: `mapper/ChartMapper.java`, `service/HistoryService.java`, `Ticker.java`, `Tickers.java` (imports), tests `PriceBarTest`, `PriceBarResamplerTest`, `HistoryServiceTest`, `TradingDateTest`
- Test: extend `src/test/java/io/github/dimazigel/yfinance/market/PriceBarTest.java` and `HistoryServiceTest`

**Interfaces (new shapes):**
- `record PriceBar(Instant timestamp, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, Optional<BigDecimal> adjClose, Optional<Long> volume)` — OHLC non-null (survey: present in every sampled bar that had a close; the mapper now drops a bar if *any* of OHLC is null and logs the count); `adjusted()` keeps its semantics with `adjClose` optional; `require(...)` is removed.
- `record HistoryMetadata(Symbol symbol, QuoteCurrency currency, String exchangeName, String fullExchangeName, String instrumentType, ZoneId timezone, Instant firstTradeDate, BigDecimal regularMarketPrice, BigDecimal previousClose, Instant regularMarketTime, int priceHint, Optional<Interval> dataGranularity, List<Range> validRanges, TradingPeriods currentTradingPeriod, boolean hasPrePostMarketData)` — all 14/14 in the chart survey are non-null; `dataGranularity` stays `Optional` because Yahoo could serve an interval string this version's `Interval` enum does not know (a documented deviation from design §4.4, preferable to a downgrade of the whole history). `ChartMapper.mapMetadata` throws `YFDataException("chart metadata incomplete: …")` if a non-null field is absent — history has no downgrade tier.
- `Dividend(Instant date, BigDecimal amount)`, `Split(Instant date, BigDecimal numerator, BigDecimal denominator, String ratio)`, `CapitalGain(Instant date, BigDecimal amount)` — all non-null (chart events always carry both); `localDate(ZoneId)` returns `LocalDate` non-null.

- [ ] **Step 1: Write/adjust the failing tests** — in `PriceBarTest`: `adjusted()` with `Optional` fields; new `barsWithAnyNullOhlcAreDropped` in `HistoryServiceTest` (fixture row with `open: null` → dropped and DEBUG `Dropped 1 of 3 bars without a complete OHLC`); `metadataIsNonNull` asserting `meta.currency().code()`, `meta.regularMarketTime()`, `meta.currentTradingPeriod().regular().start()`; `incompleteMetadataThrows` (chart with `meta` lacking `currency` → `YFDataException`); update `TradingDateTest`, `PriceBarResamplerTest` constructors (`Optional.of(...)`/`Optional.empty()`; volume sum over present values, empty when none present).

- [ ] **Step 2: Run** → compilation FAILS on the moved/changed types.

- [ ] **Step 3: Implement** — move files with `git mv`, change the records as above, update `ChartMapper` (`mapBars` drops on any null OHLC; `mapMetadata` builds non-null fields or throws; `MapperSupport.symbolOr` etc. unchanged), `PriceBarResampler` (`Optional` volume/adjClose), `HistoryService`/`Ticker`/`Tickers` imports.

- [ ] **Step 4: Run** → `./gradlew spotlessApply build` PASS.

- [ ] **Step 5: Commit** — `"market package: non-null OHLC and metadata, Optional adjClose/volume, drop-on-incomplete rule"`.

---

### Task 14: Options — per-row survey, then a re-typed `OptionContract`

**Files:**
- Create: `src/integrationTest/java/io/github/dimazigel/yfinance/CaptureOptionFixtures.java` (live, `CAPTURE_FIXTURES=1`), fixtures `src/test/resources/fixtures/options/options_<SAFE>.json` for `AAPL`, `SPY`, `^SPX`, `GLD`, `PLUG`, `TSLA`
- Move `model/OptionChain.java`, `OptionContract.java` → `market/`; modify `mapper/OptionsMapper.java`, `service/OptionsService.java`, `dto/options/OptionChainResponse.java` (per-row keys as measured)
- Test: `src/test/java/io/github/dimazigel/yfinance/service/OptionsServiceTest.java` (rewrite against the real `options_AAPL.json`)

- [ ] **Step 1: Capture and measure** — run the capture (same pattern as Task 4, endpoint `/v7/finance/options/{symbol}`; for `AAPL` also capture one non-nearest expiration via `?date=<second expirationDate>`). Then measure per-row key presence across all captured `calls`+`puts`:
```bash
python3 - <<'EOF'
import json,glob,collections
rows=[]; 
for f in glob.glob('src/test/resources/fixtures/options/*.json'):
    for res in json.load(open(f))['optionChain'].get('result') or []:
        for o in res.get('options') or []: rows += o.get('calls',[]) + o.get('puts',[])
cnt=collections.Counter(k for r in rows for k,v in r.items() if v not in (None,"",{},[]))
print(len(rows),"rows"); [print(f"{k:28s}{100*n/len(rows):6.1f}%") for k,n in sorted(cnt.items(), key=lambda x:-x[1])]
EOF
```
Rule (Intrinsic): a key present in **100 %** of rows → non-null; otherwise `Optional`. Record the table in the plan's commit message and in the appendix (Task 18) as a new section "OptionContract".

- [ ] **Step 2: Write the failing test** — `OptionsServiceTest` against `options_AAPL.json`: `contractSymbol`, `strike`, `expiration`, `type` non-null on every contract; each key measured at 100 % asserted non-null; each key below 100 % typed `Optional` and asserted present for the first ITM call; `Optional<OptionChain>` empty for a captured chain with no `expirationDates` (craft by editing the fixture: `expirationDates: []`, `options: []`).

- [ ] **Step 3: Implement** — `record OptionContract(String contractSymbol, OptionType type, BigDecimal strike, Instant expiration, <100 %-keys non-null…>, Optional<…> for the rest)`; `OptionsService.getOptionChain(Symbol[, Instant]): Optional<OptionChain>` (empty when `expirationDates` is empty); `Ticker.options()`/`options(Instant)` → `Optional<OptionChain>`; `OptionsMapper` drops a contract lacking a non-null key and logs the count.

- [ ] **Step 4: Run** → `./gradlew spotlessApply build` PASS. **Step 5: Commit** — `"Options: per-row survey, re-typed OptionContract, Optional<OptionChain> for instruments without listed options"`.

---

### Task 15: Financial statements — `fundamentals` package and the `Equity` proof token

**Files:**
- Move `model/FinancialStatement.java` → `fundamentals/`; create `fundamentals/package-info.java`
- Modify: `service/FundamentalsService.java` (`getStatement(Equity, StatementType, Frequency)`; keep a package-private `getStatement(Symbol, …)` for tests and the live drift check), `Ticker.java` (`statements(Equity, StatementType, Frequency)`), tests

- [ ] **Step 1: Failing test** — `FundamentalsServiceTest.acceptsOnlyEquities()`: `service.getStatement(equity, INCOME, ANNUAL)` sends `/ws/fundamentals-timeseries/v1/finance/timeseries/AAPL`; there is deliberately no overload taking an `Etf` — assert at compile time by not writing one (document in the test's Javadoc), and assert the existing `TRAILING + BALANCE_SHEET → IllegalArgumentException` still holds.
- [ ] **Step 2–4:** implement, run, `build` PASS. **Step 5: Commit** — `"Financial statements take an Equity proof token; fundamentals package"`.

---

## Phase 4 — Facade, removal, live suite, docs

### Task 16: Facade (`YFinance`, `Ticker`, `Tickers`) and removal of the old model

**Files:**
- Modify: `YFinance.java`, `Ticker.java`, `Tickers.java`; create `exception/YFClassMismatchException.java`
- Delete: `model/*` (whole package incl. `Required`), `service/QuoteService.java`, `HoldersService.java`, `AnalysisService.java`, `mapper/QuoteSummaryMapper.java`, `QuoteMapper.java`, `HoldersMapper.java`, `AnalysisMapper.java`, `dto/quote/*`, `dto/quotesummary/*`, the DTO-typed methods on `QuoteApi`/`QuoteSummaryApi`, `Tickers.Result`; tests `QuoteServiceTest`, `HoldersAnalysisServiceTest`, `RequiredTest`, fixtures `quotesummary_*.json`, `quote_*.json`
- Test: rewrite `YFinanceTest`, `TickersTest`; add `TickerTest`

**Interfaces (final facade, design §5):**
```java
// YFinance
public Batch<Instrument> instruments(Collection<Symbol> symbols);
public <I extends Instrument> Batch<I> instruments(Collection<Symbol> symbols, Class<I> as);
public Batch<EquityDetail> details(Collection<Equity> equities);          // + Etf, MutualFund, Crypto overloads
public Batch<PriceHistory> histories(Collection<Symbol> symbols, Range range, Interval interval);
public Batch<FinancialStatement> statements(Collection<Equity> equities, StatementType type, Frequency frequency);
public Batch<Optional<OptionChain>> options(Collection<Symbol> symbols);
public SearchResult search(String query);  public List<LookupQuote> lookup(String query, LookupType type);   // unchanged (SearchResult/LookupQuote move to a `search` package with Optional leaves)
public Ticker ticker(String|Symbol);  public Tickers tickers(String...|List<Symbol>);

// Ticker
public Instrument instrument();
public <I extends Instrument> I as(Class<I> type);                        // YFClassMismatchException(actual) otherwise
public EquityDetail detail(Equity e); public EtfDetail detail(Etf e); public MutualFundDetail detail(MutualFund f); public CryptoDetail detail(Crypto c);   // Outcome.orElseThrow
public PriceHistory history(HistoryRequest|Range,Interval|Instant,Instant,Interval);  public List<Dividend> dividends(); public List<Split> splits();
public Optional<OptionChain> options(); public Optional<OptionChain> options(Instant expiration);
public FinancialStatement statements(Equity proof, StatementType type, Frequency frequency);
public List<NewsArticle> news();

// Tickers
public <T> Batch<T> fetch(Function<? super Ticker, T> fetcher);   // Ok / Failed only (a fetcher returning null -> Failed(YFDataException "no data"))
public Batch<Instrument> instruments();  public Batch<PriceHistory> histories(Range, Interval);
```
`YFClassMismatchException extends YFDataException { AssetClass actual(); Class<?> requested(); }`.

- [ ] **Step 1: Write the failing tests** — `TickerTest`: `instrument()` for AAPL is an `Equity`; `as(Equity.class)` returns it; `as(Etf.class)` throws `YFClassMismatchException` with `actual() == EQUITY`; `detail(equity)` returns `EquityDetail`; `options()` empty for `EURUSD=X` fixture; `statements(equity, …)`. `YFinanceTest`: `instruments(...)` batch across classes; `instruments(..., Equity.class)`; `details(List.of(aapl))`; `options(...)`; `histories(...)` order preserved. `TickersTest`: `fetch(Ticker::dividends)` → `Batch`, a throwing fetcher → `Failed`, `null` → `Failed`.
- [ ] **Step 2: Run** → compile FAILS. **Step 3: Implement** the facade; delete the old classes; fix every compile error the deletions surface (there will be many — that is the point of the task); make `SearchResult`/`LookupQuote` use `Optional` and move them to `search/`.
- [ ] **Step 4:** `./gradlew spotlessApply build` PASS (coverage floor included). **Step 5: Commit** — `"New facade over the typed model; remove the flat model, Result and require helpers"`.

---

### Task 17: Live suite rewrite and the guarantee-drift detector

**Files:**
- Rewrite: `src/integrationTest/java/io/github/dimazigel/yfinance/LiveYahooIntegrationTest.java`
- Create: `src/integrationTest/java/io/github/dimazigel/yfinance/GuaranteeDriftTest.java`, `src/integrationTest/resources/survey-symbols.txt`

- [ ] **Step 1: Symbol list** — `survey-symbols.txt`: one `CLASS SYMBOL` per line for the 282 live instruments of the wide survey (the seven lists in the spec's evidence file, `docs/superpowers/specs/2026-09-26-yahoo-field-survey.md`, minus `RIDE` and `WISH`).
- [ ] **Step 2: `GuaranteeDriftTest`** (`@Tag("live")`):
```java
@Test void everyClassStillClassifiesAtLeastNinetyNinePercent() {
    var byClass = read("survey-symbols.txt");                       // Map<AssetClass, List<Symbol>>
    var batch = yf.instruments(byClass.values().stream().flatMap(List::stream).toList());
    for (var e : byClass.entrySet()) {
        long live = e.getValue().stream().filter(s -> batch.get(s).flatMap(Outcome::value).isPresent()).count();
        long downgraded = e.getValue().stream().filter(s -> batch.get(s).flatMap(Outcome::value).filter(Unclassified.class::isInstance).isPresent()).count();
        var offenders = e.getValue().stream().filter(s -> batch.get(s).flatMap(Outcome::value).filter(Unclassified.class::isInstance).isPresent())
                .map(s -> s + "=" + ((Unclassified) batch.get(s).get().orElseThrow()).missing()).toList();
        assertThat(downgraded).as("%s downgraded: %s", e.getKey(), offenders).isLessThanOrEqualTo(Math.max(1, live / 100));
    }
}
```
(The one tolerated downgrade per class is the preferred share / the odd dead name; a second one names the field that Yahoo stopped sending.)
- [ ] **Step 3: `LiveYahooIntegrationTest`** — regroup by area: `Instruments` (one instrument per class asserting the class's non-null fields; UCITS ETF classifies via fallback; preferred share → `Unclassified` naming `marketCap`), `Details` (AAPL, SPY, VFIAX, BTC-USD complete; `SAP.DE` complete — non-US equity), `History` (existing tests adapted to `Optional` volume/adjClose), `Options` (`AAPL` present, `EURUSD=X` empty, `^SPX` present), `Statements` (equity token), `Batch` (`instruments` across classes with an unknown symbol → `Skipped(UNKNOWN_SYMBOL)`; `instruments(..., Equity.class)`), `Configuration`, `Errors`.
- [ ] **Step 4:** `./gradlew integrationTest` — all green; run twice to confirm no flakiness from time-of-day fields. **Step 5: Commit** — `"Live suite for the typed model and a weekly guarantee-drift detector over the 282-symbol survey"`.

---

### Task 18: Appendix conformance test and appendix corrections

**Files:**
- Create: `src/test/java/io/github/dimazigel/yfinance/assembly/specs/AppendixConformanceTest.java`
- Modify: `docs/superpowers/specs/2026-09-26-typed-instrument-model-appendix.md`

- [ ] **Step 1: Apply the corrections discovered during Phases 1–3** to the appendix: `legalType` moves to the ETF detail table; the MutualFund rows marked *detail* move under a "MutualFund — detail" heading; `analysts.rating.averageAnalystRating` becomes `O` inside the rating cluster (Task 12); `currentDividend.yield`, `yield`, `expenseRatio` rows note the per-path unit (`v7 … |PERCENT`) with the value verified in Task 7; add the "OptionContract" table from Task 14; add "PriceBar/HistoryMetadata" rows from Task 13 (incl. the `dataGranularity` `Optional` deviation).
- [ ] **Step 2: Write the test** — parse every `| \`name\` | kind |` row of the appendix into `(table, name, kind)`; map `R`→`REQUIRED`, `O`→`OPTIONAL`, `C:x`→`OPTIONAL` with cluster `x`, `C:x(R)`→`REQUIRED` with cluster `x`, `L`→`LIST`; for each table assert the corresponding in-code list (`CoreSpecs.CORE`, `SessionSpecs.SESSION`, `BookSpecs.BOOK`, `EquitySpecs` own rows, `EquityDetailSpecs.DETAIL`, `EtfSpecs` own rows, `FundDetailSpecs.COMMON`, `MutualFundSpecs`/`MutualFundDetailSpecs`, `CryptoSpecs`+`CryptoDetailSpecs`, `FutureSpecs`) has exactly the same `(name, kind, cluster)` set; rows written with `.*` or `.{a,b}` expand to a prefix check: every code spec starting with the prefix must have the row's kind and cluster. The test reads the appendix via `Path.of("docs/superpowers/specs/2026-09-26-typed-instrument-model-appendix.md")` (Gradle runs tests with the project dir as CWD).
- [ ] **Step 3:** run; fix whichever side is wrong — the appendix is normative, but where the code was corrected by evidence in Steps 1–3 the appendix follows the code. **Step 4: Commit** — `"Appendix conformance test; appendix updated with implementation findings"`.

---

### Task 19: Documentation, final gates, PR

**Files:**
- Rewrite: `README.md` (quick start, "What's covered" table, model section explaining the hierarchy, tiers, `Optional` clusters, downgrade, `Batch`/`Outcome`, two depths and their request costs, the `QuoteCurrency` pence note, the ETF expense-ratio caveat), `AGENTS.md` (architecture bullets: `instrument`/`detail`/`assembly`/`batch`/`market`/`fundamentals`; conventions: no `@Nullable` in the model, appendix is normative, how to add a field = appendix row + spec + builder + conformance test; live drift test), project memory notes.
- [ ] **Step 1:** write the docs. **Step 2:** `./gradlew spotlessApply build integrationTest` all green; `actionlint` on workflows (unchanged, sanity). **Step 3: Commit** — `"Docs for the typed instrument model"`. **Step 4:** push the branch, open the PR (title "Typed instrument model: sealed asset-class hierarchy assembled from both Yahoo endpoints", body summarising the design decisions, the breaking changes, and the test plan with counts), wait for `build` + CodeQL, merge only when the owner says so.

---

## Self-review notes (run after writing; fixed inline)

- **Spec coverage:** D1 assembly → Tasks 3, 9, 12; D2 downgrade → 5, 9; D3 scope → 13–15; D4 shape → 5–8; D5 Intrinsic → 6–8 tables + 18; D6 no `@Nullable` → every record task + 16; D7 options discovered → 14; D8 statements token → 15; D9 post-market optional → 6; D10 ETF metrics individual → 7; §5 API → 16; §6 data flow → 9, 12; §7 errors → 4 (`YFHttpException`), 16 (`YFClassMismatchException`); §8 testing → fixtures 4, conformance 18, drift 17; §9 removal → 16; §10 open items → `QuoteCurrency` (2), option rows (14), units verified (7), MF US-only caveat (19 docs).
- **Type consistency:** `Payload(Symbol, Optional<JsonNode>, Map<String, JsonNode>)` used identically in 3, 5–12; `Resolved` accessor names (`decimal/optDecimal/longValue/optLong/intValue/optInt/string/optString/bool/instant/optInstant/date/optDate/list/clusterPresent/has/missingRequired`) used consistently; `Outcome.ok/skipped/failed` and `Batch.values/skipped/failed/get/summary` consistent between 1, 9, 12, 16; `TrailingDividend` is top-level from Task 7 on (Task 6 text updated to say so); `Rating.averageAnalystRating` is `Optional<String>` from Task 12 on.
- **Deviations from the spec recorded for the appendix (Task 18):** `legalType` to detail; `dataGranularity` `Optional`; `Rating.averageAnalystRating` optional; per-path units.
