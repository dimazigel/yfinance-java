package io.github.dimazigel.yfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.enums.EventType;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.LineItem;
import io.github.dimazigel.yfinance.enums.LookupType;
import io.github.dimazigel.yfinance.enums.OptionType;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.http.AdaptiveRateLimitConfig;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.model.FinancialStatement;
import io.github.dimazigel.yfinance.model.PriceBar;
import io.github.dimazigel.yfinance.service.HistoryRequest;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
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
 * run with {@code ./gradlew integrationTest} (also weekly in CI, see {@code live.yml}).
 *
 * <p>Assertions are intentionally loose — they verify shape and types, not exact (changing)
 * values. Coverage goal: every public entry point on {@link YFinance}, {@link Ticker} and
 * {@link Tickers}, every enum value that changes a request, non-US and non-equity instruments,
 * the {@code @Nullable} paths, and the error paths that surface Yahoo's own message.
 */
@Tag("live")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveYahooIntegrationTest {

    private YFinance yf;
    private Ticker aapl;

    @BeforeAll
    void setUp() {
        yf = YFinance.create();
        aapl = yf.ticker("AAPL");
    }

    @AfterAll
    void tearDown() {
        yf.close();
    }

    @Nested
    class History {

        @Test
        void dailyHistoryWithMetadata() {
            var history = aapl.history(Range.ONE_MONTH, Interval.ONE_DAY);
            assertThat(history.bars()).isNotEmpty();
            assertThat(history.bars().getFirst().close()).isNotNull();
            assertThat(history.metadata().currency()).isEqualTo(Currency.getInstance("USD"));
            assertThat(history.metadata().timezone()).isEqualTo(ZoneId.of("America/New_York"));
            assertThat(history.metadata().symbol()).isEqualTo(Symbol.of("AAPL"));
            assertThat(history.zoneId()).isEqualTo(ZoneId.of("America/New_York"));

            var meta = history.metadata();
            assertThat(meta.dataGranularity()).isEqualTo(Interval.ONE_DAY);
            assertThat(meta.validRanges()).contains(Range.ONE_DAY, Range.ONE_MONTH, Range.MAX);
            assertThat(meta.regularMarketTime()).isNotNull();
            assertThat(meta.priceHint()).isNotNull();
            assertThat(meta.hasPrePostMarketData()).isTrue();
            var sessions = meta.currentTradingPeriod();
            assertThat(sessions).isNotNull();
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
            assertThat(firstAdjusted.close()).isEqualByComparingTo(first.adjClose());
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
            assertThat(history.metadata().currency()).isEqualTo(Currency.getInstance("EUR"));
            assertThat(history.metadata().timezone()).isEqualTo(ZoneId.of("Europe/Berlin"));
        }

        @Test
        void penceQuotedInstrumentDoesNotAbort() {
            // London quotes in GBp (pence), which is not an ISO currency: mapped leniently to null.
            var history = yf.ticker("BP.L").history(Range.ONE_MONTH, Interval.ONE_DAY);
            assertThat(history.bars()).isNotEmpty();
            assertThat(history.metadata().currency()).satisfiesAnyOf(
                    c -> assertThat(c).isNull(),
                    c -> assertThat(c).isEqualTo(Currency.getInstance("GBP")));
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
    class Info {

        @Test
        void infoAssemblesEveryModule() {
            var info = aapl.info();

            assertThat(info.profile()).isNotNull();
            assertThat(info.profile().sector()).isNotBlank();
            assertThat(info.profile().website()).isNotNull();
            assertThat(info.profile().officers()).isNotEmpty();

            var quote = info.quote();
            assertThat(quote.symbol()).isEqualTo(Symbol.of("AAPL"));
            assertThat(quote.currency()).isEqualTo(Currency.getInstance("USD"));
            assertThat(quote.price().regularMarketPrice()).isPositive();
            assertThat(quote.price().marketCap()).isPositive();
            assertThat(quote.keyStats().trailingEps()).isNotNull();
            assertThat(quote.keyStats().sharesOutstanding()).isPositive();
            assertThat(quote.analyst().recommendationKey()).isNotBlank();

            assertThat(info.recommendationTrend()).isNotEmpty();
            assertThat(info.upgradesDowngrades()).isNotEmpty();
            assertThat(info.earningsDates()).isNotEmpty();
            assertThat(info.secFilings()).isNotEmpty();
            assertThat(info.secFilings().getFirst().url()).isNotNull();
        }

        @Test
        void indexInfoDegradesToQuoteOnly() {
            nonEquityInfo("^GSPC", "INDEX");
        }

        @Test
        void etfInfoDegradesToQuoteOnly() {
            nonEquityInfo("SPY", "ETF");
        }

        /**
         * quoteSummary is inconsistent for non-equities (the same request may 404 "No fundamentals
         * data found" or succeed), so info() falls back to /v7/finance/quote. Either way the caller
         * must get a populated quote; the profile and analyst coverage are the @Nullable paths.
         */
        private void nonEquityInfo(String symbol, String expectedQuoteType) {
            var ticker = yf.ticker(symbol);
            var info = ticker.info();
            assertThat(info.quote().symbol()).isEqualTo(Symbol.of(symbol));
            assertThat(info.quote().quoteType()).isEqualTo(expectedQuoteType);
            assertThat(info.quote().price().regularMarketPrice()).isPositive();
            assertThat(info.quote().price().previousClose()).isPositive();
            assertThat(info.quote().currency()).isEqualTo(Currency.getInstance("USD"));
            assertThat(info.upgradesDowngrades()).isEmpty();
            if (info.profile() != null) {
                assertThat(info.profile().sector()).as("non-equities have no sector").isNull();
            }
        }

        @Test
        void lightweightQuoteWorksForEveryAssetClass() {
            var quote = aapl.quote();
            assertThat(quote.longName()).isEqualTo("Apple Inc.");
            assertThat(quote.price().regularMarketPrice()).isPositive();
            assertThat(quote.keyStats().trailingPe()).isPositive();
            assertThat(quote.keyStats().dividendYield()).isBetween(
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE); // a fraction, not a percentage
            assertThat(quote.analyst().recommendationKey()).isNotBlank();
            assertThat(quote.analyst().recommendationMean()).isBetween(
                    java.math.BigDecimal.ONE, java.math.BigDecimal.valueOf(5));

            assertThat(yf.ticker("BTC-USD").quote().quoteType()).isEqualTo("CRYPTOCURRENCY");
            assertThatThrownBy(() -> yf.ticker("NO_SUCH_SYMBOL_XYZ").quote())
                    .isInstanceOf(YFDataException.class)
                    .hasMessageContaining("NO_SUCH_SYMBOL_XYZ");
        }
    }

    @Nested
    class Fundamentals {

        @Test
        void everyStatementTypeAtAnnualAndQuarterly() {
            for (StatementType type : StatementType.values()) {
                for (Frequency frequency : List.of(Frequency.ANNUAL, Frequency.QUARTERLY)) {
                    var statement = aapl.financials(type, frequency);
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
            var income = aapl.financials(StatementType.INCOME, Frequency.TRAILING);
            assertThat(income.periods()).isNotEmpty();
            assertThat(income.value(LineItem.TOTAL_REVENUE, income.periods().getLast())).isPositive();

            var cashFlow = aapl.financials(StatementType.CASH_FLOW, Frequency.TRAILING);
            assertThat(cashFlow.periods()).isNotEmpty();

            // Yahoo has no trailing balance sheet; the library rejects the combination up front.
            assertThatThrownBy(() -> aapl.financials(StatementType.BALANCE_SHEET, Frequency.TRAILING))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void lineItemKeysHaveNotDrifted() {
            // Guards against Yahoo renaming keys: most typed line items must resolve for a large
            // industrial company. (Financial-sector keys like NetLoan legitimately stay absent.)
            for (StatementType type : StatementType.values()) {
                var statement = aapl.financials(type, Frequency.ANNUAL);
                LocalDate latest = statement.periods().getLast();
                var items = LineItem.forStatement(type);
                long present = items.stream().filter(li -> statement.value(li, latest) != null).count();
                assertThat(present)
                        .as("%s: %d of %d line items present at %s", type, present, items.size(), latest)
                        .isGreaterThanOrEqualTo(items.size() / 2);
            }
        }

        @Test
        void unknownLineItemOrPeriodIsNull() {
            FinancialStatement income = aapl.financials(StatementType.INCOME, Frequency.ANNUAL);
            assertThat(income.value("NoSuchLineItem", income.periods().getLast())).isNull();
            assertThat(income.value(LineItem.TOTAL_REVENUE, LocalDate.of(1990, 1, 1))).isNull();
        }
    }

    @Nested
    class Options {

        @Test
        void nearestChainAndExpirations() {
            var chain = aapl.optionChain();
            assertThat(chain.underlyingSymbol()).isEqualTo(Symbol.of("AAPL"));
            assertThat(chain.expirationDates()).hasSizeGreaterThan(2).isSorted();
            assertThat(chain.expiration()).isEqualTo(chain.expirationDates().getFirst());
            assertThat(chain.calls()).isNotEmpty();
            assertThat(chain.puts()).isNotEmpty();

            assertThat(aapl.optionExpirations()).isEqualTo(chain.expirationDates());
        }

        @Test
        void chainForASpecificExpiration() {
            Instant second = aapl.optionExpirations().get(1);
            var chain = aapl.optionChain(second);

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
    class Holders {

        @Test
        void allHolderSections() {
            var holders = aapl.holders();
            assertThat(holders.breakdown()).isNotNull();
            assertThat(holders.breakdown().institutionsPercentHeld()).isBetween(
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE);
            assertThat(holders.institutional()).isNotEmpty()
                    .allSatisfy(h -> assertThat(h.organization()).isNotBlank());
            assertThat(holders.mutualFund()).isNotEmpty();
            assertThat(holders.insiderTransactions()).isNotEmpty();
            assertThat(holders.insiderRoster()).isNotEmpty();
            assertThat(holders.netSharePurchaseActivity()).isNotNull();
        }
    }

    @Nested
    class Analysis {

        @Test
        void priceTargetsAreOrdered() {
            var target = aapl.analystPriceTargets();
            assertThat(target).isNotNull();
            assertThat(target.low()).isLessThanOrEqualTo(target.mean());
            assertThat(target.mean()).isLessThanOrEqualTo(target.high());
            assertThat(target.numberOfAnalysts()).isPositive();
        }

        @Test
        void estimatesTrendsAndHistory() {
            assertThat(aapl.earningsEstimate()).isNotEmpty()
                    .anySatisfy(e -> assertThat(e.average()).isNotNull());
            assertThat(aapl.revenueEstimate()).isNotEmpty()
                    .anySatisfy(e -> assertThat(e.average()).isPositive());
            assertThat(aapl.earningsHistory()).isNotEmpty()
                    .allSatisfy(h -> assertThat(h.quarter()).isNotNull());
            assertThat(aapl.epsTrend()).isNotEmpty();
            assertThat(aapl.epsRevisions()).isNotEmpty();
            assertThat(aapl.growthEstimates()).isNotEmpty();
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
                        assertThat(n.title()).isNotBlank();
                        assertThat(n.link()).isNotNull();
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
        void fanOutKeepsSuccessesWhenOneSymbolIsBogus() {
            var results = yf.tickers("AAPL", "NO_SUCH_SYMBOL_XYZ").infos();
            assertThat(results).containsOnlyKeys(Symbol.of("AAPL"), Symbol.of("NO_SUCH_SYMBOL_XYZ"));
            assertThat(results.get(Symbol.of("AAPL")).isSuccess()).isTrue();

            var failure = results.get(Symbol.of("NO_SUCH_SYMBOL_XYZ"));
            assertThat(failure.isSuccess()).isFalse();
            assertThat(failure).isInstanceOfSatisfying(Tickers.Result.Failure.class, f -> {
                assertThat(f.error()).isInstanceOf(YFDataException.class).hasMessageContaining("NO_SUCH_SYMBOL_XYZ");
                assertThatThrownBy(failure::orElseThrow).isSameAs(f.error());
            });
        }

        @Test
        void fetchFansOutAnyTickerMethod() {
            var dividends = yf.tickers("AAPL", "MSFT", "KO").withConcurrency(3).fetch(Ticker::dividends);
            assertThat(dividends).hasSize(3).allSatisfy((symbol, r) ->
                    assertThat(r.orElseThrow()).as(symbol.value()).isNotEmpty());
        }

        @Test
        void batchQuotesInOneRequestAcrossAssetClasses() {
            var quotes = yf.quotes("AAPL", "^GSPC", "EURUSD=X", "ES=F", "SAP.DE", "NO_SUCH_SYMBOL_XYZ");
            assertThat(quotes.keySet()).extracting(Symbol::value)
                    .containsExactly("AAPL", "^GSPC", "EURUSD=X", "ES=F", "SAP.DE"); // order kept, unknown omitted
            assertThat(quotes.values()).allSatisfy(q -> assertThat(q.price().regularMarketPrice()).isPositive());
            assertThat(quotes.get(Symbol.of("SAP.DE")).currency()).isEqualTo(Currency.getInstance("EUR"));
            assertThat(quotes.get(Symbol.of("ES=F")).quoteType()).isEqualTo("FUTURE");
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
            assertThat(results).hasSize(4).allSatisfy((symbol, r) -> {
                assertThat(r.isSuccess()).as("%s: %s", symbol, r).isTrue();
                assertThat(r.orElseThrow().bars()).as(symbol.value()).isNotEmpty();
            });
            assertThat(results.get(Symbol.of("BTC-USD")).orElseThrow().metadata().instrumentType())
                    .isEqualTo("CRYPTOCURRENCY");
            assertThat(results.get(Symbol.of("EURUSD=X")).orElseThrow().metadata().currency())
                    .isEqualTo(Currency.getInstance("USD"));
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
