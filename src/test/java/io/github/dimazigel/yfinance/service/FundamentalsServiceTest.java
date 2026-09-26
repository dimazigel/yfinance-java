package io.github.dimazigel.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.api.FundamentalsApi;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.fundamentals.FinancialStatement;
import io.github.dimazigel.yfinance.instrument.Core;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.MarketState;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.instrument.Session;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
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
        Equity equity = equityOf("AAPL");

        FinancialStatement stmt = service.getStatement(equity, StatementType.INCOME, Frequency.ANNUAL);

        assertThat(stmt.type()).isEqualTo(StatementType.INCOME);
        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath())
                .isEqualTo("/ws/fundamentals-timeseries/v1/finance/timeseries/AAPL");
    }

    @Test
    void equityOverloadStillRejectsTrailingBalanceSheet() {
        assertThatThrownBy(() -> service.getStatement(
                        equityOf("AAPL"), StatementType.BALANCE_SHEET, Frequency.TRAILING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing")
                .hasMessageContaining("balance sheet");
        assertThat(server.getRequestCount()).isZero();
    }

    /** Minimal {@link Equity} built with the canonical constructor; only {@code symbol} matters here. */
    private static Equity equityOf(String symbol) {
        Core core = new Core(
                Symbol.of(symbol),
                "Apple Inc.",
                Optional.of("Apple Inc."),
                QuoteCurrency.of("USD"),
                "NMS",
                "NasdaqGS",
                ZoneId.of("America/New_York"),
                MarketState.REGULAR,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                Instant.EPOCH,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                1L,
                1L,
                Instant.EPOCH,
                2,
                true);
        return new Equity(
                core,
                new Session(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, 1L),
                Optional.empty(),
                new Equity.Valuation(BigDecimal.TEN, 1L, 1L, QuoteCurrency.of("USD")),
                new Equity.NextEarnings(Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, false),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Instant.EPOCH);
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
        assertThat(stmt.value(io.github.dimazigel.yfinance.enums.LineItem.TOTAL_REVENUE, LocalDate.parse("2023-09-30")))
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
        assertThat(stmt.value("TotalRevenue", LocalDate.parse("2023-09-30"))).isEqualByComparingTo("2");
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
}
