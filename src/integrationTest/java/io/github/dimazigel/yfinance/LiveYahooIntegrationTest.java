package io.github.dimazigel.yfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.enums.EventType;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.LineItem;
import io.github.dimazigel.yfinance.enums.LookupType;
import io.github.dimazigel.yfinance.enums.OptionType;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.exception.YFClassMismatchException;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.fundamentals.FinancialStatement;
import io.github.dimazigel.yfinance.http.AdaptiveRateLimitConfig;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Crypto;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Future;
import io.github.dimazigel.yfinance.instrument.FxPair;
import io.github.dimazigel.yfinance.instrument.Index;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.instrument.Unclassified;
import io.github.dimazigel.yfinance.market.PriceBar;
import io.github.dimazigel.yfinance.service.HistoryRequest;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Smoke tests that hit the real Yahoo Finance API. Excluded from the default {@code test} task;
 * run with {@code ./gradlew integrationTest} (also weekly in CI, see {@code live.yml}; the drift
 * detector over the wide 282-symbol survey lives separately in {@link GuaranteeDriftTest}).
 *
 * <p>Assertions are intentionally loose — they verify shape and types, not exact (changing)
 * values, and target each class's <em>guaranteed</em> (non-null) fields and the documented
 * {@code Optional}s (present/empty), never exact prices. Coverage goal: every public entry point on
 * {@link YFinance}, {@link Ticker} and {@link Tickers}, every one of the seven typed instrument
 * classes, non-US and non-equity instruments, the {@code Optional} paths, and the error paths that
 * surface Yahoo's own message.
 */
@Tag("live")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveYahooIntegrationTest {

    /** One instrument per class, plus a UCITS ETF and a preferred share, classified in one request. */
    private static final List<Symbol> CLASSIFICATION_SYMBOLS = List.of(
            Symbol.of("AAPL"), Symbol.of("SPY"), Symbol.of("VFIAX"), Symbol.of("^GSPC"),
            Symbol.of("BTC-USD"), Symbol.of("EURUSD=X"), Symbol.of("ES=F"),
            Symbol.of("CSPX.L"), Symbol.of("BAC-PL"));

    private YFinance yf;
    private Ticker aapl;
    private Equity aaplEquity;
    private io.github.dimazigel.yfinance.batch.Batch<io.github.dimazigel.yfinance.instrument.Instrument> classified;

    @BeforeAll
    void setUp() {
        yf = YFinance.create();
        aapl = yf.ticker("AAPL");
        classified = yf.instruments(CLASSIFICATION_SYMBOLS);
        aaplEquity = (Equity) classified.get(Symbol.of("AAPL")).orElseThrow().orElseThrow();
    }

    @AfterAll
    void tearDown() {
        yf.close();
    }

    @Nested
    class Instruments {

        @Test
        void equityHasItsGuaranteedFields() {
            assertThat(aaplEquity.symbol()).isEqualTo(Symbol.of("AAPL"));
            assertThat(aaplEquity.core().currency().code()).isEqualTo("USD");
            assertThat(aaplEquity.core().price()).isPositive();
            assertThat(aaplEquity.session().open()).isPositive();
            assertThat(aaplEquity.valuation().marketCap()).isPositive();
            assertThat(aaplEquity.valuation().sharesOutstanding()).isPositive();
            assertThat(aaplEquity.valuation().financialCurrency().code()).isEqualTo("USD");
            assertThat(aaplEquity.nextEarnings().expected()).isAfter(Instant.EPOCH);
        }

        @Test
        void etfHasItsGuaranteedFields() {
            var spy = (Etf) classified.get(Symbol.of("SPY")).orElseThrow().orElseThrow();
            assertThat(spy.core().currency().code()).isEqualTo("USD");
            assertThat(spy.session().volume()).isPositive();
            assertThat(spy.ytdReturn()).isNotNull();
            assertThat(spy.threeMonthReturn()).isNotNull();
        }

        @Test
        void mutualFundHasItsGuaranteedFields() {
            var vfiax = (MutualFund) classified.get(Symbol.of("VFIAX")).orElseThrow().orElseThrow();
            assertThat(vfiax.netAssets()).isPositive();
            assertThat(vfiax.expenseRatio()).isNotNull();
            assertThat(vfiax.yield()).isNotNull();
            assertThat(vfiax.dividendRate()).isNotNull();
            assertThat(vfiax.ytdReturn()).isNotNull();
            assertThat(vfiax.threeMonthReturn()).isNotNull();
        }

        @Test
        void indexHasItsGuaranteedFields() {
            var gspc = (Index) classified.get(Symbol.of("^GSPC")).orElseThrow().orElseThrow();
            assertThat(gspc.core().price()).isPositive();
            assertThat(gspc.session().dayHigh()).isPositive();
        }

        @Test
        void cryptoHasItsGuaranteedFields() {
            var btc = (Crypto) classified.get(Symbol.of("BTC-USD")).orElseThrow().orElseThrow();
            assertThat(btc.marketCap()).isPositive();
            assertThat(btc.supply().circulating()).isPositive();
            assertThat(btc.toCurrency().code()).isEqualTo("USD");
            assertThat(btc.branding().image()).isNotNull();
            assertThat(btc.startDate()).isBefore(LocalDate.now());
        }

        @Test
        void fxPairHasItsGuaranteedFields() {
            var eurusd = (FxPair) classified.get(Symbol.of("EURUSD=X")).orElseThrow().orElseThrow();
            assertThat(eurusd.core().price()).isPositive();
            assertThat(eurusd.session().dayLow()).isPositive();
        }

        @Test
        void futureHasItsGuaranteedFieldsAndNoLongName() {
            var es = (Future) classified.get(Symbol.of("ES=F")).orElseThrow().orElseThrow();
            // Futures never have a long name (Core javadoc); every other class does.
            assertThat(es.core().longName()).isEmpty();
            assertThat(es.contract().underlyingSymbol()).isNotNull();
            assertThat(es.contract().headSymbol()).isEqualTo(Symbol.of("ES=F"));
            assertThat(es.contract().expireDate()).isNotNull();
        }

        @Test
        void ucitsEtfDowngradesWhenYtdReturnIsMissingEvenAfterTheFallback() {
            // CSPX.L (iShares Core S&P 500 UCITS ETF, LSE) has no ytdReturn/threeMonthReturn on v7,
            // and — as of 2026-09 — Yahoo does not supply them for this or any other UCITS-domiciled
            // ETF in the wide survey through the single-symbol fallback either (design §6.3), so the
            // guarantee correctly downgrades rather than fabricating a value (design D2). This is
            // confirmed live: every one of the 7 European-listed ETFs in the 282-symbol survey
            // downgrades the same way (see GuaranteeDriftTest, which is what tracks whether that
            // ever changes).
            var outcome = classified.get(Symbol.of("CSPX.L")).orElseThrow().orElseThrow();
            assertThat(outcome).isInstanceOf(Unclassified.class);
            var unclassified = (Unclassified) outcome;
            assertThat(unclassified.attempted()).contains(AssetClass.ETF);
            assertThat(unclassified.missing()).contains("ytdReturn", "threeMonthReturn");
        }

        @Test
        void preferredShareDowngradesNamingTheMissingField() {
            // BAC-PL is a preferred share Yahoo reports as EQUITY but without a market cap.
            var outcome = classified.get(Symbol.of("BAC-PL")).orElseThrow().orElseThrow();
            assertThat(outcome).isInstanceOf(Unclassified.class);
            var unclassified = (Unclassified) outcome;
            assertThat(unclassified.attempted()).contains(AssetClass.EQUITY);
            assertThat(unclassified.missing()).contains("marketCap");
        }

        @Test
        void unknownSymbolThrowsYfDataException() {
            assertThatThrownBy(() -> yf.ticker("NO_SUCH_SYMBOL_XYZ").instrument())
                    .isInstanceOf(YFDataException.class)
                    .hasMessageContaining("NO_SUCH_SYMBOL_XYZ");
        }

        @Test
        void asWrongClassThrowsClassMismatchNamingTheActualClass() {
            assertThatThrownBy(() -> yf.ticker("SPY").as(Equity.class))
                    .isInstanceOf(YFClassMismatchException.class)
                    .satisfies(e -> assertThat(((YFClassMismatchException) e).actual()).isEqualTo(AssetClass.ETF));
        }
    }

    @Nested
    class Details {

        @Test
        void equityDetailAssemblesAllModules() {
            var detail = aapl.detail(aaplEquity);
            assertThat(detail.profile().sector()).isNotBlank();
            assertThat(detail.profile().industry()).isNotBlank();
            assertThat(detail.profile().country()).isNotBlank();
            assertThat(detail.statistics().floatShares()).isPositive();
            assertThat(detail.financials().totalRevenue()).isPositive();
            assertThat(detail.analysts().recommendationTrend()).isNotEmpty();
            assertThat(detail.ownership().institutions()).isNotEmpty();
        }

        @Test
        void nonUsEquityDetailAssembles() {
            // SAP.DE (Xetra) — the assembly must not assume US-only field presence.
            var sap = yf.ticker("SAP.DE");
            var sapEquity = sap.as(Equity.class);
            var detail = sap.detail(sapEquity);
            assertThat(detail.profile().country()).isEqualTo("Germany");
            assertThat(detail.profile().sector()).isNotBlank();
            assertThat(detail.financials().totalRevenue()).isPositive();
        }

        @Test
        void etfDetailAssembles() {
            var spyEtf = (Etf) classified.get(Symbol.of("SPY")).orElseThrow().orElseThrow();
            var detail = yf.ticker("SPY").detail(spyEtf);
            assertThat(detail.family()).isNotBlank();
            assertThat(detail.holdings()).isNotEmpty();
            assertThat(detail.trailingReturns().ytd()).isNotNull();
        }

        @Test
        void mutualFundDetailAssembles() {
            var fund = (MutualFund) classified.get(Symbol.of("VFIAX")).orElseThrow().orElseThrow();
            var detail = yf.ticker("VFIAX").detail(fund);
            assertThat(detail.family()).isNotBlank();
            assertThat(detail.holdings()).isNotEmpty();
            assertThat(detail.morningstar()).isNotNull();
        }

        @Test
        void cryptoDetailAssembles() {
            var btc = (Crypto) classified.get(Symbol.of("BTC-USD")).orElseThrow().orElseThrow();
            var detail = yf.ticker("BTC-USD").detail(btc);
            assertThat(detail.name()).isNotBlank();
            assertThat(detail.website()).isNotNull();
            assertThat(detail.startDate()).isNotNull();
        }
    }

    @Nested
    class History {

        @Test
        void dailyHistoryWithMetadata() {
            var history = aapl.history(Range.ONE_MONTH, Interval.ONE_DAY);
            assertThat(history.bars()).isNotEmpty();
            assertThat(history.bars().getFirst().close()).isNotNull();
            assertThat(history.metadata().currency().code()).isEqualTo("USD");
            assertThat(history.metadata().timezone()).isEqualTo(ZoneId.of("America/New_York"));
            assertThat(history.metadata().symbol()).isEqualTo(Symbol.of("AAPL"));
            assertThat(history.zoneId()).isEqualTo(ZoneId.of("America/New_York"));

            var meta = history.metadata();
            assertThat(meta.dataGranularity()).contains(Interval.ONE_DAY);
            assertThat(meta.validRanges()).contains(Range.ONE_DAY, Range.ONE_MONTH, Range.MAX);
            assertThat(meta.regularMarketTime()).isNotNull();
            assertThat(meta.hasPrePostMarketData()).isTrue();
            var sessions = meta.currentTradingPeriod();
            assertThat(sessions.regular().start()).isBefore(sessions.regular().end());
            assertThat(sessions.pre().end()).isBeforeOrEqualTo(sessions.regular().start());
            assertThat(sessions.regular().end()).isBeforeOrEqualTo(sessions.post().start());
        }

        @Test
        void adjustedHistoryMatchesAutoAdjustSemantics() {
            var raw = aapl.history(Range.MAX, Interval.ONE_DAY);
            var adjusted = raw.adjusted();

            // Decades of splits: the earliest adjusted close is a fraction of the raw close.
            var first = raw.bars().getFirst();
            var firstAdjusted = adjusted.bars().getFirst();
            assertThat(firstAdjusted.close()).isEqualByComparingTo(first.adjClose().orElseThrow());
            assertThat(firstAdjusted.close()).isLessThan(first.close());
            assertThat(firstAdjusted.volume()).isEqualTo(first.volume());
            // The latest bar needs (almost) no adjustment.
            var last = raw.bars().getLast();
            assertThat(adjusted.bars().getLast().close()).isCloseTo(last.close(), org.assertj.core.data.Percentage.withPercentage(1));
            assertThat(adjusted.dividends()).isEqualTo(raw.dividends());
        }

        @Test
        void backfillWindowWithExplicitStartAndEnd() {
            var end = Instant.now();
            var start = end.minus(Duration.ofDays(30));
            var history = aapl.history(start, end, Interval.ONE_DAY);
            assertThat(history.bars()).isNotEmpty();
            assertThat(history.bars().getFirst().timestamp()).isAfterOrEqualTo(start.minus(Duration.ofDays(1)));
            assertThat(history.bars().getLast().timestamp()).isBefore(end);
        }

        @Test
        void openEndedPeriodRunsToNow() {
            var start = Instant.now().minus(Duration.ofDays(10));
            var history = aapl.history(HistoryRequest.builder(aapl.symbol())
                    .period(start, null).interval(Interval.ONE_DAY).build());
            assertThat(history.bars()).isNotEmpty();
        }

        @Test
        void thirtyMinuteHistoryIsReallyThirtyMinutes() {
            var bars = aapl.history(Range.FIVE_DAYS, Interval.THIRTY_MINUTES).bars();

            assertThat(bars).hasSizeGreaterThan(10);
            // Every bar starts on a :00/:30 boundary, and consecutive bars within a session are 30m apart.
            assertThat(bars).allSatisfy(b -> assertThat(b.timestamp().getEpochSecond() % 1800).isZero());
            assertThat(countSteps(bars, Duration.ofMinutes(30))).isGreaterThan(bars.size() / 2);
        }

        @Test
        void oneMinuteAndHourlyIntraday() {
            var minute = aapl.history(Range.ONE_DAY, Interval.ONE_MINUTE).bars();
            assertThat(minute).hasSizeGreaterThan(50);
            assertThat(countSteps(minute, Duration.ofMinutes(1))).isGreaterThan(minute.size() / 2);

            var hourly = aapl.history(Range.FIVE_DAYS, Interval.ONE_HOUR).bars();
            assertThat(hourly).hasSizeGreaterThan(5);
            assertThat(countSteps(hourly, Duration.ofHours(1))).isGreaterThan(hourly.size() / 2);
        }

        @Test
        void weeklyAndMonthlyIntervals() {
            // Yahoo appends an in-progress bar stamped with the latest trade time, so the final gap
            // can be short; every completed bar must still be one period apart.
            var weekly = aapl.history(Range.ONE_YEAR, Interval.ONE_WEEK).bars();
            assertThat(weekly).hasSizeBetween(40, 60);
            assertThat(minGap(completed(weekly))).isGreaterThanOrEqualTo(Duration.ofDays(6));

            var monthly = aapl.history(Range.FIVE_YEARS, Interval.ONE_MONTH).bars();
            assertThat(monthly).hasSizeBetween(55, 65);
            assertThat(minGap(completed(monthly))).isGreaterThanOrEqualTo(Duration.ofDays(27));
        }

        @Test
        void includePrePostAddsExtendedHoursBars() {
            var regular = aapl.history(HistoryRequest.builder(aapl.symbol())
                    .range(Range.FIVE_DAYS).interval(Interval.ONE_HOUR).includePrePost(false).build());
            var extended = aapl.history(HistoryRequest.builder(aapl.symbol())
                    .range(Range.FIVE_DAYS).interval(Interval.ONE_HOUR).includePrePost(true).build());
            assertThat(extended.bars().size()).isGreaterThan(regular.bars().size());
        }

        @Test
        void eventsSubsetOnlyReturnsRequestedEvents() {
            var history = aapl.history(HistoryRequest.builder(aapl.symbol())
                    .range(Range.MAX).interval(Interval.THREE_MONTHS)
                    .events(Set.of(EventType.DIVIDENDS)).build());
            assertThat(history.dividends()).isNotEmpty();
            assertThat(history.splits()).isEmpty(); // AAPL has split; it was not requested
        }

        @Test
        void dividendsAndSplitsConveniencesWithExchangeLocalDates() {
            var dividends = aapl.dividends();
            assertThat(dividends).hasSizeGreaterThan(50);
            assertThat(dividends).allSatisfy(d -> {
                assertThat(d.amount()).isPositive();
                assertThat(d.localDate(ZoneId.of("America/New_York"))).isNotNull();
            });
            assertThat(dividends).isSortedAccordingTo((a, b) -> a.date().compareTo(b.date()));

            var splits = aapl.splits();
            assertThat(splits).hasSizeGreaterThanOrEqualTo(4); // 1987, 2000, 2005, 2014, 2020
            assertThat(splits).allSatisfy(s -> {
                assertThat(s.numerator()).isPositive();
                assertThat(s.denominator()).isPositive();
                assertThat(s.ratio()).matches("\\d+:\\d+");
            });
        }

        @Test
        void mutualFundHistoryWithCapitalGainsRequested() {
            // As of 2026-09 Yahoo returns no capitalGains chart events even for funds that distribute
            // them (FCNTX, VFIAX, AGTHX all come back dividends-only), so only the request shape and
            // the mapping of whatever is present can be verified.
            var fund = yf.ticker("FCNTX");
            var history = fund.history(HistoryRequest.builder(fund.symbol())
                    .range(Range.MAX).interval(Interval.THREE_MONTHS)
                    .events(Set.of(EventType.DIVIDENDS, EventType.CAPITAL_GAINS)).build());
            assertThat(history.metadata().instrumentType()).isEqualTo("MUTUALFUND");
            assertThat(history.dividends()).isNotEmpty();
            assertThat(history.capitalGains()).allSatisfy(g -> {
                assertThat(g.amount()).isPositive();
                assertThat(g.localDate(history.zoneId())).isNotNull();
            });
        }

        @Test
        void nonUsInstrumentCarriesItsOwnCurrencyAndTimezone() {
            var history = yf.ticker("SAP.DE").history(Range.ONE_MONTH, Interval.ONE_DAY);
            assertThat(history.bars()).isNotEmpty();
            assertThat(history.metadata().currency().code()).isEqualTo("EUR");
            assertThat(history.metadata().timezone()).isEqualTo(ZoneId.of("Europe/Berlin"));
        }

        @Test
        void penceQuotedInstrumentDoesNotAbort() {
            // London quotes in GBp (pence), which is not an ISO currency: the raw code is kept as-is,
            // with iso() empty rather than the whole history failing.
            var history = yf.ticker("BP.L").history(Range.ONE_MONTH, Interval.ONE_DAY);
            assertThat(history.bars()).isNotEmpty();
            assertThat(history.metadata().currency().code()).isEqualTo("GBp");
            assertThat(history.metadata().currency().iso()).isEmpty();
            assertThat(history.metadata().timezone()).isEqualTo(ZoneId.of("Europe/London"));
        }

        @Test
        void symbolIsNormalisedBeforeTheRequest() {
            var history = yf.ticker("  msft ").history(Range.FIVE_DAYS, Interval.ONE_DAY);
            assertThat(history.metadata().symbol()).isEqualTo(Symbol.of("MSFT"));
        }

        private static long countSteps(List<PriceBar> bars, Duration step) {
            return IntStream.range(1, bars.size())
                    .filter(i -> Duration.between(bars.get(i - 1).timestamp(), bars.get(i).timestamp()).equals(step))
                    .count();
        }

        private static List<PriceBar> completed(List<PriceBar> bars) {
            return bars.subList(0, bars.size() - 1);
        }

        private static Duration minGap(List<PriceBar> bars) {
            return IntStream.range(1, bars.size())
                    .mapToObj(i -> Duration.between(bars.get(i - 1).timestamp(), bars.get(i).timestamp()))
                    .min(Duration::compareTo)
                    .orElseThrow();
        }
    }

    @Nested
    class Options {

        @Test
        void nearestChainAndExpirations() {
            var chain = aapl.options().orElseThrow();
            assertThat(chain.underlyingSymbol()).isEqualTo(Symbol.of("AAPL"));
            assertThat(chain.expirationDates()).hasSizeGreaterThan(2).isSorted();
            assertThat(chain.expiration()).isEqualTo(chain.expirationDates().getFirst());
            assertThat(chain.calls()).isNotEmpty();
            assertThat(chain.puts()).isNotEmpty();
        }

        @Test
        void fxHasNoListedOptions() {
            assertThat(yf.ticker("EURUSD=X").options()).as("FX has no listed options").isEmpty();
        }

        @Test
        void indexOptionChainIsPresent() {
            // ^SPX (S&P 500 index options, CBOE) — unlike most indices, this one lists options.
            var chain = yf.ticker("^SPX").options().orElseThrow();
            assertThat(chain.underlyingSymbol()).isEqualTo(Symbol.of("^SPX"));
            assertThat(chain.calls()).isNotEmpty();
            assertThat(chain.puts()).isNotEmpty();
        }

        @Test
        void chainForASpecificExpiration() {
            Instant second = aapl.options().orElseThrow().expirationDates().get(1);
            var chain = aapl.options(second).orElseThrow();

            assertThat(chain.expiration()).isEqualTo(second);
            assertThat(chain.calls()).allSatisfy(c -> {
                assertThat(c.type()).isEqualTo(OptionType.CALL);
                assertThat(c.strike()).isPositive();
                assertThat(c.contractSymbol()).startsWith("AAPL");
                assertThat(c.expiration()).isEqualTo(second);
            });
            assertThat(chain.puts()).allSatisfy(p -> assertThat(p.type()).isEqualTo(OptionType.PUT));
            assertThat(chain.calls()).anyMatch(c -> c.inTheMoney()).anyMatch(c -> !c.inTheMoney());
        }
    }

    @Nested
    class Statements {

        @Test
        void everyStatementTypeAtAnnualAndQuarterly() {
            for (StatementType type : StatementType.values()) {
                for (Frequency frequency : List.of(Frequency.ANNUAL, Frequency.QUARTERLY)) {
                    var statement = aapl.statements(aaplEquity, type, frequency);
                    assertThat(statement.periods()).as("%s %s periods", type, frequency).isNotEmpty();
                    assertThat(statement.lineItems()).as("%s %s line items", type, frequency).isNotEmpty();
                    assertThat(statement.type()).isEqualTo(type);
                    assertThat(statement.frequency()).isEqualTo(frequency);
                }
            }
        }

        @Test
        void trailingTwelveMonthsForIncomeAndCashFlow() {
            // Yahoo serves trailing (TTM) figures as a series, one value per quarter-end.
            var income = aapl.statements(aaplEquity, StatementType.INCOME, Frequency.TRAILING);
            assertThat(income.periods()).isNotEmpty();
            assertThat(income.value(LineItem.TOTAL_REVENUE, income.periods().getLast()).orElseThrow()).isPositive();

            var cashFlow = aapl.statements(aaplEquity, StatementType.CASH_FLOW, Frequency.TRAILING);
            assertThat(cashFlow.periods()).isNotEmpty();

            // Yahoo has no trailing balance sheet; the library rejects the combination up front.
            assertThatThrownBy(() -> aapl.statements(aaplEquity, StatementType.BALANCE_SHEET, Frequency.TRAILING))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lineItemKeysHaveNotDrifted() {
            // Guards against Yahoo renaming keys: most typed line items must resolve for a large
            // industrial company. (Financial-sector keys like NetLoan legitimately stay absent.)
            for (StatementType type : StatementType.values()) {
                var statement = aapl.statements(aaplEquity, type, Frequency.ANNUAL);
                LocalDate latest = statement.periods().getLast();
                var items = LineItem.forStatement(type);
                long present = items.stream().filter(li -> statement.value(li, latest).isPresent()).count();
                assertThat(present)
                        .as("%s: %d of %d line items present at %s", type, present, items.size(), latest)
                        .isGreaterThanOrEqualTo(items.size() / 2);
            }
        }

        @Test
        void unknownLineItemOrPeriodIsEmpty() {
            FinancialStatement income = aapl.statements(aaplEquity, StatementType.INCOME, Frequency.ANNUAL);
            assertThat(income.value("NoSuchLineItem", income.periods().getLast())).isEmpty();
            assertThat(income.value(LineItem.TOTAL_REVENUE, LocalDate.of(1990, 1, 1))).isEmpty();
        }
    }

    @Nested
    class SearchAndLookup {

        @Test
        void searchReturnsQuotesAndNews() {
            var result = yf.search("apple");
            assertThat(result.quotes()).extracting(q -> q.symbol()).contains(Symbol.of("AAPL"));
            assertThat(result.news()).isNotEmpty()
                    .allSatisfy(n -> {
                        assertThat(n.title()).isPresent();
                        assertThat(n.link()).isPresent();
                    });
        }

        @Test
        void tickerNewsIsSymbolSpecific() {
            assertThat(aapl.news()).isNotEmpty();
        }

        @Test
        void lookupByType() {
            assertThat(yf.lookup("apple", LookupType.EQUITY))
                    .extracting(q -> q.symbol()).contains(Symbol.of("AAPL"));
            assertThat(yf.lookup("spy", LookupType.ETF))
                    .extracting(q -> q.symbol()).contains(Symbol.of("SPY"));
            assertThat(yf.lookup("bitcoin", LookupType.CRYPTOCURRENCY))
                    .extracting(q -> q.symbol()).contains(Symbol.of("BTC-USD"));
            assertThat(yf.lookup("dow jones", LookupType.INDEX)).isNotEmpty();
            assertThat(yf.lookup("apple", LookupType.ALL)).isNotEmpty();
        }
    }

    @Nested
    class Batch {

        @Test
        void instrumentsSkipsAnUnknownSymbolAndKeepsTheOthers() {
            var batch = yf.tickers("AAPL", "NO_SUCH_SYMBOL_XYZ").instruments();
            assertThat(batch.outcomes()).extracting(Outcome::symbol)
                    .containsExactly(Symbol.of("AAPL"), Symbol.of("NO_SUCH_SYMBOL_XYZ"));
            assertThat(batch.values()).singleElement().isInstanceOf(Equity.class);
            assertThat(batch.skipped()).singleElement().satisfies(s -> {
                assertThat(s.symbol()).isEqualTo(Symbol.of("NO_SUCH_SYMBOL_XYZ"));
                assertThat(s.reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL);
                assertThatThrownBy(s::orElseThrow).isInstanceOf(YFDataException.class).hasMessageContaining("NO_SUCH_SYMBOL_XYZ");
            });
        }

        @Test
        void typedInstrumentsNarrowToOneClass() {
            var batch = yf.instruments(
                    List.of(Symbol.of("AAPL"), Symbol.of("SPY"), Symbol.of("NO_SUCH_SYMBOL_XYZ")), Equity.class);
            assertThat(batch.values()).extracting(Equity::symbol).containsExactly(Symbol.of("AAPL"));
            assertThat(batch.skipped()).extracting(Outcome.Skipped::symbol, Outcome.Skipped::reason)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(Symbol.of("SPY"), SkipReason.WRONG_ASSET_CLASS),
                            org.assertj.core.groups.Tuple.tuple(Symbol.of("NO_SUCH_SYMBOL_XYZ"), SkipReason.UNKNOWN_SYMBOL));
        }

        @Test
        void fetchFansOutAnyTickerMethod() {
            var dividends = yf.tickers("AAPL", "MSFT", "KO").withConcurrency(3).fetch(Ticker::dividends);
            assertThat(dividends.size()).isEqualTo(3);
            assertThat(dividends.failed()).isEmpty();
            assertThat(dividends.values()).allSatisfy(d -> assertThat(d).isNotEmpty());
        }

        @Test
        void instrumentsInOneRequestAcrossAssetClasses() {
            var batch = yf.instruments(List.of(Symbol.of("AAPL"), Symbol.of("^GSPC"), Symbol.of("EURUSD=X"),
                    Symbol.of("ES=F"), Symbol.of("SAP.DE"), Symbol.of("NO_SUCH_SYMBOL_XYZ")));
            assertThat(batch.values()).extracting(i -> i.symbol().value())
                    .containsExactly("AAPL", "^GSPC", "EURUSD=X", "ES=F", "SAP.DE"); // order kept, unknown skipped
            assertThat(batch.values()).allSatisfy(i -> assertThat(i.core().price()).isPositive());
            assertThat(batch.values()).extracting(i -> i.assetClass()).containsExactly(AssetClass.EQUITY,
                    AssetClass.INDEX, AssetClass.FX, AssetClass.FUTURE, AssetClass.EQUITY);
            assertThat(batch.get(Symbol.of("SAP.DE")).orElseThrow().orElseThrow().core().currency().code()).isEqualTo("EUR");
        }

        @Test
        void historiesAcrossAssetClasses() {
            // Index, crypto, FX and futures all share the chart endpoint but have different metadata.
            var tickers = yf.tickers(List.of(
                    Symbol.of("^GSPC"), Symbol.of("BTC-USD"), Symbol.of("EURUSD=X"), Symbol.of("ES=F")))
                    .withConcurrency(2);
            assertThat(tickers.symbols()).hasSize(4);
            assertThat(tickers.ticker(Symbol.of("BTC-USD")).symbol()).isEqualTo(Symbol.of("BTC-USD"));

            var results = tickers.histories(Range.ONE_MONTH, Interval.ONE_DAY);
            assertThat(results.size()).isEqualTo(4);
            assertThat(results.failed()).isEmpty();
            assertThat(results.values()).allSatisfy(h -> assertThat(h.bars()).as(h.metadata().symbol().value()).isNotEmpty());
            assertThat(results.get(Symbol.of("BTC-USD")).orElseThrow().orElseThrow().metadata().instrumentType())
                    .isEqualTo("CRYPTOCURRENCY");
            assertThat(results.get(Symbol.of("EURUSD=X")).orElseThrow().orElseThrow().metadata().currency().code())
                    .isEqualTo("USD");
        }
    }

    @Nested
    class Configuration {

        @Test
        void clientCustomizerSeesEveryRequestIncludingTheHandshake() {
            var requests = new AtomicInteger();
            var config = EndpointConfig.production().withClientCustomizer(b -> b.addInterceptor(chain -> {
                requests.incrementAndGet();
                return chain.proceed(chain.request());
            }));
            try (var custom = YFinance.create(config)) {
                custom.ticker("AAPL").history(Range.FIVE_DAYS, Interval.ONE_DAY);
            }
            assertThat(requests.get()).as("cookie + crumb + chart").isGreaterThanOrEqualTo(3);
        }

        @Test
        void disabledRateLimitingStillWorks() {
            var config = EndpointConfig.production()
                    .withAdaptiveRateLimit(AdaptiveRateLimitConfig.disabled())
                    .withCallTimeout(Duration.ofSeconds(20));
            try (var plain = YFinance.create(config)) {
                assertThat(plain.ticker("AAPL").history(Range.FIVE_DAYS, Interval.ONE_DAY).bars()).isNotEmpty();
            }
        }
    }

    @Nested
    class Errors {

        @Test
        void unknownSymbolSurfacesYahooReasonNotTheEnvelope() {
            assertThatThrownBy(() -> yf.ticker("NO_SUCH_SYMBOL_XYZ").history(Range.ONE_MONTH, Interval.ONE_DAY))
                    .isInstanceOf(YFDataException.class)
                    .hasMessageContaining("NO_SUCH_SYMBOL_XYZ")
                    .satisfies(e -> assertThat(e.getMessage())
                            .as("Yahoo's description is shown verbatim, not a record toString or raw JSON")
                            .doesNotContain("ChartError[", "{\"chart\""));
        }

        @Test
        void unsupportedIntervalRangeComboSurfacesYahooReason() {
            // Yahoo only serves 1m bars for the last ~7 days per request.
            assertThatThrownBy(() -> aapl.history(Range.THREE_MONTHS, Interval.ONE_MINUTE))
                    .isInstanceOf(YFDataException.class)
                    .hasMessageContaining("1m");
        }
    }
}
