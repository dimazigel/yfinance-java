package io.github.dimazigel.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.api.FundamentalsApi;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.LineItem;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.fundamentals.FinancialStatement;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.Instruments;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FundamentalsServiceTest {

    private MockWebServer server;
    private FundamentalsService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        service = new FundamentalsService(Fixtures.api(server, FundamentalsApi.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    /**
     * Statements are equities-only (design D8): the public overload takes an {@link Equity},
     * proof at compile time that this instrument belongs to the one class Yahoo's timeseries
     * endpoint actually serves. There is deliberately no overload taking an {@code Etf},
     * {@code MutualFund}, or any other {@code Instrument} subtype — that absence is itself the
     * compile-time assertion this test documents; it isn't (and can't be) expressed as a runtime
     * check, since code calling {@code service.getStatement(someEtf, ...)} simply fails to compile.
     */
    @Test
    void acceptsOnlyEquities() throws Exception {
        server.enqueue(Fixtures.jsonResponse("timeseries_income_annual.json"));
        Equity equity = Instruments.equity("AAPL");

        FinancialStatement stmt = service.getStatement(equity, StatementType.INCOME, Frequency.ANNUAL);

        assertThat(stmt.type()).isEqualTo(StatementType.INCOME);
        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath())
                .isEqualTo("/ws/fundamentals-timeseries/v1/finance/timeseries/AAPL");
    }

    @Test
    void equityOverloadStillRejectsTrailingBalanceSheet() {
        assertThatThrownBy(() -> service.getStatement(
                        Instruments.equity("AAPL"), StatementType.BALANCE_SHEET, Frequency.TRAILING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing")
                .hasMessageContaining("balance sheet");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void parsesTimeseriesIntoStatement() {
        server.enqueue(Fixtures.jsonResponse("timeseries_income_annual.json"));

        FinancialStatement stmt = service.getStatement(Symbol.of("AAPL"), StatementType.INCOME, Frequency.ANNUAL);

        assertThat(stmt.type()).isEqualTo(StatementType.INCOME);
        assertThat(stmt.frequency()).isEqualTo(Frequency.ANNUAL);
        assertThat(stmt.periods()).containsExactly(LocalDate.parse("2022-09-30"), LocalDate.parse("2023-09-30"));

        assertThat(stmt.value("TotalRevenue", LocalDate.parse("2023-09-30")).orElseThrow())
                .isEqualByComparingTo("383285000000");
        assertThat(stmt.value(io.github.dimazigel.yfinance.enums.LineItem.TOTAL_REVENUE, LocalDate.parse("2023-09-30")).orElseThrow())
                .isEqualByComparingTo("383285000000");
        assertThat(stmt.value("TotalRevenue", LocalDate.parse("2022-09-30")).orElseThrow())
                .isEqualByComparingTo("394328000000");
        // NetIncome has no value for the first period (null datapoint); unknown line items and periods are empty too
        assertThat(stmt.value("NetIncome", LocalDate.parse("2022-09-30"))).isEmpty();
        assertThat(stmt.value("NoSuchLineItem", LocalDate.parse("2023-09-30"))).isEmpty();
        assertThat(stmt.value("NetIncome", LocalDate.parse("2023-09-30")).orElseThrow())
                .isEqualByComparingTo("96995000000");
    }

    @Test
    void financialStatementCollectionsAreImmutable() {
        server.enqueue(Fixtures.jsonResponse("timeseries_income_annual.json"));

        FinancialStatement stmt = service.getStatement(Symbol.of("AAPL"), StatementType.INCOME, Frequency.ANNUAL);

        assertThatThrownBy(() -> stmt.periods().add(LocalDate.parse("2024-09-30")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> stmt.lineItems().put("Other", Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> stmt.lineItems()
                        .get("TotalRevenue")
                        .put(LocalDate.parse("2024-09-30"), java.math.BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void usesInjectedClockForPeriod2() throws Exception {
        server.enqueue(Fixtures.jsonResponse("timeseries_income_annual.json"));
        var fixed = java.time.Clock.fixed(java.time.Instant.ofEpochSecond(1_750_000_000L), java.time.ZoneOffset.UTC);
        var clockedService = new FundamentalsService(
                Fixtures.api(server, io.github.dimazigel.yfinance.api.FundamentalsApi.class), fixed);

        clockedService.getStatement(Symbol.of("AAPL"), StatementType.INCOME, Frequency.ANNUAL);

        assertThat(server.takeRequest().getRequestUrl().queryParameter("period2"))
                .isEqualTo("1750000000");
    }

    @Test
    void buildsPrefixedTypeParamAndPath() throws Exception {
        server.enqueue(Fixtures.jsonResponse("timeseries_income_annual.json"));

        service.getStatement(Symbol.of("AAPL"), StatementType.INCOME, Frequency.ANNUAL);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath())
                .isEqualTo("/ws/fundamentals-timeseries/v1/finance/timeseries/AAPL");
        String type = req.getRequestUrl().queryParameter("type");
        assertThat(type).contains("annualTotalRevenue").contains("annualNetIncome");
        assertThat(req.getRequestUrl().queryParameter("period1")).isNotNull();
        assertThat(req.getRequestUrl().queryParameter("period2")).isNotNull();
    }

    @Test
    void trailingBalanceSheetIsRejectedWithoutARequest() {
        // Yahoo has no trailing (TTM) balance sheet: the request would 404 with a confusing
        // "No timeseries type(s) specified". Fail fast and explain instead.
        assertThatThrownBy(() -> service.getStatement(
                        Symbol.of("AAPL"), StatementType.BALANCE_SHEET, Frequency.TRAILING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing")
                .hasMessageContaining("balance sheet");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void malformedAsOfDateIsSkippedNotThrown() {
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                "{\"timeseries\":{\"result\":[{\"meta\":{\"symbol\":[\"AAPL\"],\"type\":[\"annualTotalRevenue\"]},"
                        + "\"annualTotalRevenue\":["
                        + "{\"asOfDate\":\"not-a-date\",\"reportedValue\":{\"raw\":1}},"
                        + "{\"asOfDate\":\"2023-09-30\",\"reportedValue\":{\"raw\":2}}]}],\"error\":null}}"));

        FinancialStatement stmt = service.getStatement(Symbol.of("AAPL"), StatementType.INCOME, Frequency.ANNUAL);

        assertThat(stmt.periods()).containsExactly(LocalDate.parse("2023-09-30"));
        assertThat(stmt.value("TotalRevenue", LocalDate.parse("2023-09-30")).orElseThrow()).isEqualByComparingTo("2");
    }

    @Test
    void skippedPointsAreLoggedAtDebug() {
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                "{\"timeseries\":{\"result\":[{\"meta\":{\"symbol\":[\"AAPL\"],\"type\":[\"annualTotalRevenue\"]},"
                        + "\"annualTotalRevenue\":["
                        + "{\"asOfDate\":\"not-a-date\",\"reportedValue\":{\"raw\":1}},"
                        + "{\"asOfDate\":\"2023-09-30\",\"reportedValue\":{\"raw\":2}}]}],\"error\":null}}"));

        try (var log = io.github.dimazigel.yfinance.testsupport.LogCapture.ofLibrary()) {
            service.getStatement(Symbol.of("AAPL"), StatementType.INCOME, Frequency.ANNUAL);

            assertThat(log.messages(ch.qos.logback.classic.Level.DEBUG))
                    .anySatisfy(m -> assertThat(m).isEqualTo("Unparseable date \"not-a-date\"; left null"))
                    .anySatisfy(m -> assertThat(m).isEqualTo("Skipped 1 fundamentals point without a usable date"));
        }
    }

    // --- batch B, item 3: several statements in one request ---

    @Test
    void multiStatementRequestJoinsEveryPairAndSkipsTrailingBalanceSheet() throws Exception {
        server.enqueue(Fixtures.jsonResponse("timeseries_multi.json"));

        var result = service.getStatements(Symbol.of("AAPL"),
                Set.of(StatementType.INCOME, StatementType.BALANCE_SHEET, StatementType.CASH_FLOW),
                Set.of(Frequency.ANNUAL, Frequency.QUARTERLY, Frequency.TRAILING));

        assertThat(server.getRequestCount()).as("one timeseries request for all pairs").isEqualTo(1);
        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/ws/fundamentals-timeseries/v1/finance/timeseries/AAPL");
        List<String> keys = List.of(req.getRequestUrl().queryParameter("type").split(","));
        assertThat(keys).contains(
                "annualTotalRevenue", "quarterlyTotalRevenue", "trailingTotalRevenue",
                "annualTotalAssets", "quarterlyTotalAssets",
                "annualOperatingCashFlow", "quarterlyOperatingCashFlow", "trailingOperatingCashFlow");
        assertThat(keys).as("Yahoo has no trailing balance sheet").noneMatch(k -> k.startsWith("trailing")
                && LineItem.forStatement(StatementType.BALANCE_SHEET).stream().anyMatch(li -> k.equals("trailing" + li.key())));
        assertThat(keys).doesNotHaveDuplicates();
        int expected = LineItem.forStatement(StatementType.INCOME).size() * 3
                + LineItem.forStatement(StatementType.BALANCE_SHEET).size() * 2
                + LineItem.forStatement(StatementType.CASH_FLOW).size() * 3;
        assertThat(keys).hasSize(expected);

        assertThat(result.keySet()).containsExactlyInAnyOrder(StatementType.INCOME, StatementType.BALANCE_SHEET, StatementType.CASH_FLOW);
        assertThat(result.get(StatementType.INCOME).keySet()).containsExactlyInAnyOrder(Frequency.ANNUAL, Frequency.QUARTERLY, Frequency.TRAILING);
        assertThat(result.get(StatementType.BALANCE_SHEET).keySet()).containsExactlyInAnyOrder(Frequency.ANNUAL, Frequency.QUARTERLY);
        assertThat(result.get(StatementType.CASH_FLOW).keySet()).containsExactlyInAnyOrder(Frequency.ANNUAL, Frequency.QUARTERLY, Frequency.TRAILING);
    }

    @Test
    void multiStatementResponseIsSplitByFrequencyPrefixAndStatementKeys() {
        server.enqueue(Fixtures.jsonResponse("timeseries_multi.json"));

        var result = service.getStatements(Symbol.of("AAPL"),
                Set.of(StatementType.INCOME, StatementType.BALANCE_SHEET, StatementType.CASH_FLOW),
                Set.of(Frequency.ANNUAL, Frequency.QUARTERLY, Frequency.TRAILING));

        FinancialStatement incomeAnnual = result.get(StatementType.INCOME).get(Frequency.ANNUAL);
        assertThat(incomeAnnual.type()).isEqualTo(StatementType.INCOME);
        assertThat(incomeAnnual.frequency()).isEqualTo(Frequency.ANNUAL);
        assertThat(incomeAnnual.periods()).containsExactly(LocalDate.parse("2022-09-30"), LocalDate.parse("2023-09-30"));
        assertThat(incomeAnnual.value("TotalRevenue", LocalDate.parse("2023-09-30")).orElseThrow()).isEqualByComparingTo("383285000000");
        assertThat(incomeAnnual.value("NetIncome", LocalDate.parse("2023-09-30")).orElseThrow()).isEqualByComparingTo("96995000000");
        assertThat(incomeAnnual.lineItems()).as("balance-sheet and cash-flow keys stay out of the income statement")
                .doesNotContainKeys("TotalAssets", "OperatingCashFlow");

        FinancialStatement incomeQuarterly = result.get(StatementType.INCOME).get(Frequency.QUARTERLY);
        assertThat(incomeQuarterly.frequency()).isEqualTo(Frequency.QUARTERLY);
        assertThat(incomeQuarterly.periods()).containsExactly(LocalDate.parse("2024-03-31"), LocalDate.parse("2024-06-30"));
        assertThat(incomeQuarterly.value("TotalRevenue", LocalDate.parse("2024-06-30")).orElseThrow()).isEqualByComparingTo("85777000000");
        assertThat(incomeQuarterly.value("TotalRevenue", LocalDate.parse("2023-09-30"))).as("annual periods stay out of the quarterly statement").isEmpty();
        // NetIncome is served for the annual frequency only: the row must not leak into the other slices
        assertThat(incomeAnnual.lineItems()).containsKey("NetIncome");
        assertThat(incomeQuarterly.lineItems()).as("one-frequency-only series stays out of the quarterly slice").doesNotContainKey("NetIncome");

        FinancialStatement incomeTrailing = result.get(StatementType.INCOME).get(Frequency.TRAILING);
        assertThat(incomeTrailing.periods()).containsExactly(LocalDate.parse("2024-06-30"));
        assertThat(incomeTrailing.value("TotalRevenue", LocalDate.parse("2024-06-30")).orElseThrow()).isEqualByComparingTo("385603000000");
        assertThat(incomeTrailing.lineItems()).as("one-frequency-only series stays out of the trailing slice").doesNotContainKey("NetIncome");

        FinancialStatement balanceAnnual = result.get(StatementType.BALANCE_SHEET).get(Frequency.ANNUAL);
        assertThat(balanceAnnual.type()).isEqualTo(StatementType.BALANCE_SHEET);
        assertThat(balanceAnnual.value("TotalAssets", LocalDate.parse("2023-09-30")).orElseThrow()).isEqualByComparingTo("352583000000");
        assertThat(balanceAnnual.lineItems()).doesNotContainKeys("TotalRevenue", "NetIncome");
        assertThat(result.get(StatementType.BALANCE_SHEET).get(Frequency.QUARTERLY).periods()).containsExactly(LocalDate.parse("2024-06-30"));

        FinancialStatement cashTrailing = result.get(StatementType.CASH_FLOW).get(Frequency.TRAILING);
        assertThat(cashTrailing.type()).isEqualTo(StatementType.CASH_FLOW);
        assertThat(cashTrailing.value("OperatingCashFlow", LocalDate.parse("2024-06-30")).orElseThrow()).isEqualByComparingTo("113041000000");
        assertThat(result.get(StatementType.CASH_FLOW).get(Frequency.ANNUAL).value("OperatingCashFlow", LocalDate.parse("2023-09-30")).orElseThrow())
                .isEqualByComparingTo("110543000000");
    }

    @Test
    void multiStatementSubsetOnlyRequestsWhatWasAsked() throws Exception {
        server.enqueue(Fixtures.jsonResponse("timeseries_multi.json"));

        var result = service.getStatements(Instruments.equity("AAPL"), Set.of(StatementType.CASH_FLOW), Set.of(Frequency.QUARTERLY));

        List<String> keys = List.of(server.takeRequest().getRequestUrl().queryParameter("type").split(","));
        assertThat(keys).allMatch(k -> k.startsWith("quarterly"));
        assertThat(keys).contains("quarterlyOperatingCashFlow").doesNotContain("quarterlyTotalRevenue", "quarterlyTotalAssets");
        assertThat(result.keySet()).containsExactly(StatementType.CASH_FLOW);
        assertThat(result.get(StatementType.CASH_FLOW).keySet()).containsExactly(Frequency.QUARTERLY);
        assertThatThrownBy(() -> result.put(StatementType.INCOME, Map.of())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.get(StatementType.CASH_FLOW).put(Frequency.ANNUAL, null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void multiStatementRejectsEmptySetsAndTrailingBalanceSheetAloneWithoutARequest() {
        Equity equity = Instruments.equity("AAPL");
        assertThatThrownBy(() -> service.getStatements(equity, Set.of(), Set.of(Frequency.ANNUAL)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("types");
        assertThatThrownBy(() -> service.getStatements(equity, Set.of(StatementType.INCOME), Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("frequencies");
        assertThatThrownBy(() -> service.getStatements(equity, Set.of(StatementType.BALANCE_SHEET), Set.of(Frequency.TRAILING)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trailing").hasMessageContaining("balance sheet");
        assertThat(server.getRequestCount()).isZero();
    }
}
