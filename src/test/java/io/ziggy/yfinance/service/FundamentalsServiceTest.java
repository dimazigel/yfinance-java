package io.ziggy.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ziggy.yfinance.api.FundamentalsApi;
import io.ziggy.yfinance.enums.Frequency;
import io.ziggy.yfinance.enums.StatementType;
import io.ziggy.yfinance.model.FinancialStatement;
import io.ziggy.yfinance.testsupport.Fixtures;
import io.ziggy.yfinance.valueobject.Symbol;
import java.time.LocalDate;
import java.util.Map;
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

    @Test
    void parsesTimeseriesIntoStatement() {
        server.enqueue(Fixtures.jsonResponse("timeseries_income_annual.json"));

        FinancialStatement stmt = service.getStatement(Symbol.of("AAPL"), StatementType.INCOME, Frequency.ANNUAL);

        assertThat(stmt.type()).isEqualTo(StatementType.INCOME);
        assertThat(stmt.frequency()).isEqualTo(Frequency.ANNUAL);
        assertThat(stmt.periods()).containsExactly(LocalDate.parse("2022-09-30"), LocalDate.parse("2023-09-30"));

        assertThat(stmt.value("TotalRevenue", LocalDate.parse("2023-09-30")))
                .isEqualByComparingTo("383285000000");
        assertThat(stmt.value(io.ziggy.yfinance.enums.LineItem.TOTAL_REVENUE, LocalDate.parse("2023-09-30")))
                .isEqualByComparingTo("383285000000");
        assertThat(stmt.value("TotalRevenue", LocalDate.parse("2022-09-30")))
                .isEqualByComparingTo("394328000000");
        // NetIncome has no value for the first period (null datapoint)
        assertThat(stmt.value("NetIncome", LocalDate.parse("2022-09-30"))).isNull();
        assertThat(stmt.value("NetIncome", LocalDate.parse("2023-09-30")))
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
                Fixtures.api(server, io.ziggy.yfinance.api.FundamentalsApi.class), fixed);

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
        assertThat(stmt.value("TotalRevenue", LocalDate.parse("2023-09-30"))).isEqualByComparingTo("2");
    }
}
