package io.ziggy.yfinance;

import static org.assertj.core.api.Assertions.assertThat;

import io.ziggy.yfinance.enums.Frequency;
import io.ziggy.yfinance.enums.Interval;
import io.ziggy.yfinance.enums.LookupType;
import io.ziggy.yfinance.enums.Range;
import io.ziggy.yfinance.enums.StatementType;
import io.ziggy.yfinance.valueobject.Symbol;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Smoke tests that hit the real Yahoo Finance API. Excluded from the default {@code test} task;
 * run with {@code ./gradlew integrationTest}. Assertions are intentionally loose — they verify
 * shape and types, not exact (changing) values.
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

    @Test
    void history() {
        var history = aapl.history(Range.ONE_MONTH, Interval.ONE_DAY);
        assertThat(history.bars()).isNotEmpty();
        assertThat(history.bars().getFirst().close()).isNotNull();
        assertThat(history.metadata().currency()).isNotNull();
        assertThat(history.metadata().timezone()).isNotNull();
    }

    @Test
    void thirtyMinuteHistoryIsReallyThirtyMinutes() {
        var bars = aapl.history(Range.FIVE_DAYS, Interval.THIRTY_MINUTES).bars();

        assertThat(bars).hasSizeGreaterThan(10);
        // Every bar starts on a :00/:30 boundary, and consecutive bars within a session are 30m apart.
        assertThat(bars).allSatisfy(b -> assertThat(b.timestamp().getEpochSecond() % 1800).isZero());
        long thirtyMinuteSteps = java.util.stream.IntStream.range(1, bars.size())
                .filter(i -> bars.get(i).timestamp().getEpochSecond()
                        - bars.get(i - 1).timestamp().getEpochSecond() == 1800)
                .count();
        assertThat(thirtyMinuteSteps).isGreaterThan(bars.size() / 2);
    }

    @Test
    void info() {
        var info = aapl.info();
        assertThat(info.quote().price().regularMarketPrice()).isPositive();
        assertThat(info.profile().sector()).isNotBlank();
    }

    @Test
    void historyBackfillWindow() {
        var end = java.time.Instant.now();
        var start = end.minus(java.time.Duration.ofDays(30));
        var history = aapl.history(start, end, Interval.ONE_DAY);
        assertThat(history.bars()).isNotEmpty();
    }

    @Test
    void batchFanOutKeepsSuccessesWhenOneSymbolIsBogus() {
        var results = yf.tickers("AAPL", "NO_SUCH_SYMBOL_XYZ").infos();
        assertThat(results).containsOnlyKeys(Symbol.of("AAPL"), Symbol.of("NO_SUCH_SYMBOL_XYZ"));
        assertThat(results.get(Symbol.of("AAPL")).isSuccess()).isTrue();
        assertThat(results.get(Symbol.of("NO_SUCH_SYMBOL_XYZ")).isSuccess()).isFalse();
    }

    @Test
    void typedLineItemValue() {
        var income = aapl.financials(StatementType.INCOME, Frequency.ANNUAL);
        var latest = income.periods().get(income.periods().size() - 1);
        assertThat(income.value(io.ziggy.yfinance.enums.LineItem.TOTAL_REVENUE, latest)).isNotNull();
    }

    @Test
    void incomeStatement() {
        var income = aapl.financials(StatementType.INCOME, Frequency.ANNUAL);
        assertThat(income.periods()).isNotEmpty();
        var latest = income.periods().get(income.periods().size() - 1);
        assertThat(income.value("TotalRevenue", latest)).isNotNull();
    }

    @Test
    void optionChain() {
        var chain = aapl.optionChain();
        assertThat(chain.expirationDates()).isNotEmpty();
        assertThat(chain.calls()).isNotEmpty();
    }

    @Test
    void holders() {
        var holders = aapl.holders();
        assertThat(holders.breakdown()).isNotNull();
        assertThat(holders.insiderRoster()).isNotEmpty();
        assertThat(holders.netSharePurchaseActivity()).isNotNull();
    }

    @Test
    void analystPriceTargets() {
        assertThat(aapl.analystPriceTargets().mean()).isNotNull();
    }

    @Test
    void extendedAnalysis() {
        assertThat(aapl.earningsHistory()).isNotEmpty();
        assertThat(aapl.epsTrend()).isNotEmpty();
        assertThat(aapl.epsRevisions()).isNotEmpty();
        assertThat(aapl.growthEstimates()).isNotEmpty();
    }

    @Test
    void dividendsAndNews() {
        assertThat(aapl.dividends()).isNotEmpty();
        assertThat(aapl.news()).isNotEmpty();
    }

    @Test
    void search() {
        var result = yf.search("apple");
        assertThat(result.quotes()).isNotEmpty();
    }

    @Test
    void lookup() {
        var quotes = yf.lookup("apple", LookupType.EQUITY);
        assertThat(quotes).isNotEmpty();
        assertThat(quotes.getFirst().symbol().value()).isNotBlank();
    }

    @Test
    void unknownPeriodValueIsNull() {
        var income = aapl.financials(StatementType.INCOME, Frequency.ANNUAL);
        assertThat(income.value("NoSuchLineItem", LocalDate.of(1990, 1, 1))).isNull();
    }
}
