package io.ziggy.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ziggy.yfinance.api.ChartApi;
import io.ziggy.yfinance.enums.Interval;
import io.ziggy.yfinance.enums.Range;
import io.ziggy.yfinance.exception.YFDataException;
import io.ziggy.yfinance.model.PriceHistory;
import io.ziggy.yfinance.testsupport.Fixtures;
import io.ziggy.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HistoryServiceTest {

    private MockWebServer server;
    private HistoryService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        service = new HistoryService(Fixtures.api(server, ChartApi.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void parsesBarsDividendsSplitsAndMetadata() throws Exception {
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));

        PriceHistory history = service.getHistory(
                HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_MONTH).interval(Interval.ONE_DAY).build());

        assertThat(history.bars()).hasSize(3);
        var first = history.bars().getFirst();
        assertThat(first.timestamp()).isEqualTo(Instant.ofEpochSecond(1700000000));
        assertThat(first.open()).isEqualByComparingTo("187.0");
        assertThat(first.close()).isEqualByComparingTo("188.0");
        assertThat(first.adjClose()).isEqualByComparingTo("187.8");
        assertThat(first.volume()).isEqualTo(50_000_000L);

        assertThat(history.dividends()).singleElement().satisfies(d -> {
            assertThat(d.amount()).isEqualByComparingTo("0.24");
            assertThat(d.date()).isEqualTo(Instant.ofEpochSecond(1700000000));
        });
        assertThat(history.splits()).singleElement().satisfies(s -> {
            assertThat(s.numerator()).isEqualByComparingTo(new BigDecimal("4"));
            assertThat(s.denominator()).isEqualByComparingTo(new BigDecimal("1"));
            assertThat(s.ratio()).isEqualTo("4:1");
        });

        var meta = history.metadata();
        assertThat(meta.currency()).isEqualTo(Currency.getInstance("USD"));
        assertThat(meta.timezone()).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(meta.symbol()).isEqualTo(Symbol.of("AAPL"));
        assertThat(meta.regularMarketPrice()).isEqualByComparingTo("190.5");
    }

    @Test
    void sendsRangeIntervalAndEventParams() throws Exception {
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));

        service.getHistory(HistoryRequest.builder(Symbol.of("AAPL"))
                .range(Range.ONE_MONTH)
                .interval(Interval.ONE_DAY)
                .build());

        RecordedRequest req = server.takeRequest();
        var url = req.getRequestUrl();
        assertThat(url.encodedPath()).isEqualTo("/v8/finance/chart/AAPL");
        assertThat(url.queryParameter("range")).isEqualTo("1mo");
        assertThat(url.queryParameter("interval")).isEqualTo("1d");
        assertThat(url.queryParameter("events")).isEqualTo("div,splits,capitalGains");
    }

    @Test
    void sendsPeriodParamsWhenStartEndGiven() throws Exception {
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));

        service.getHistory(HistoryRequest.builder(Symbol.of("AAPL"))
                .interval(Interval.ONE_DAY)
                .period(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000))
                .build());

        var url = server.takeRequest().getRequestUrl();
        assertThat(url.queryParameter("period1")).isEqualTo("1000");
        assertThat(url.queryParameter("period2")).isEqualTo("2000");
        assertThat(url.queryParameter("range")).isNull();
    }

    @Test
    void emptyEventsSetOmitsEventsParam() throws Exception {
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));

        service.getHistory(HistoryRequest.builder(Symbol.of("AAPL"))
                .range(Range.ONE_MONTH)
                .events(Set.of())
                .build());

        assertThat(server.takeRequest().getRequestUrl().queryParameter("events")).isNull();
    }

    @Test
    void unknownCurrencyAndTimezoneDoNotAbortHistory() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":{\"currency\":\"XYZ\",\"symbol\":\"AAPL\","
                        + "\"exchangeTimezoneName\":\"Not/A_Zone\"},"
                        + "\"timestamp\":[1700000000],"
                        + "\"indicators\":{\"quote\":[{\"open\":[1.0],\"high\":[2.0],\"low\":[0.5],"
                        + "\"close\":[1.5],\"volume\":[100]}]}}],\"error\":null}}"));

        var history = service.getHistory(
                HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_MONTH).build());

        assertThat(history.bars()).hasSize(1);
        assertThat(history.metadata().currency()).isNull();
        assertThat(history.metadata().timezone()).isNull();
    }

    @Test
    void skipsNullCloseBarsAndKeepsMissingVolumeAsNull() {
        // Yahoo pads intraday responses with all-null rows (halts, pre-open) and sometimes
        // omits volume. Null-close rows must be dropped; missing volume must NOT become 0.
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":{\"currency\":\"USD\",\"symbol\":\"AAPL\","
                        + "\"exchangeTimezoneName\":\"America/New_York\"},"
                        + "\"timestamp\":[1700000000,1700000060,1700000120],"
                        + "\"indicators\":{\"quote\":[{"
                        + "\"open\":[187.0,null,189.1],"
                        + "\"high\":[189.0,null,191.2],"
                        + "\"low\":[186.5,null,188.0],"
                        + "\"close\":[188.0,null,190.5],"
                        + "\"volume\":[50000000,null,null]}]}}],\"error\":null}}"));

        var history = service.getHistory(
                HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_DAY).build());

        assertThat(history.bars()).hasSize(2); // null-close row dropped
        assertThat(history.bars().getFirst().volume()).isEqualTo(50_000_000L);
        assertThat(history.bars().getLast().volume()).isNull(); // missing, not zero
        assertThat(history.bars().getLast().close()).isEqualByComparingTo("190.5");
    }

    @Test
    void throwsOnErrorEnvelope() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"No data found, symbol may be delisted\"}}}"));

        assertThatThrownBy(() -> service.getHistory(
                        HistoryRequest.builder(Symbol.of("NOPE")).range(Range.ONE_MONTH).build()))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("delisted");
    }

    @Test
    void errorEnvelopeSurfacesYahooReasonVerbatim() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":null,\"error\":{\"code\":\"Unprocessable Entity\","
                        + "\"description\":\"1m data not available for startTime=1 and endTime=2.\"}}}"));

        assertThatThrownBy(() -> service.getHistory(
                        HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_MONTH).build()))
                .isInstanceOf(YFDataException.class)
                .hasMessage("Yahoo error for AAPL: 1m data not available for startTime=1 and endTime=2.");
    }

    @Test
    void thirtyMinuteBarsAreFetchedAs15mAndResampled() throws Exception {
        // Yahoo returns 60m bars when asked for 30m, so (like yfinance) fetch 15m and resample.
        long t0 = 1_699_999_200L; // 30m bucket boundary
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":{\"currency\":\"USD\",\"symbol\":\"AAPL\"},"
                        + "\"timestamp\":[" + t0 + "," + (t0 + 900) + "," + (t0 + 1800) + "],"
                        + "\"indicators\":{\"quote\":[{"
                        + "\"open\":[10,11,12],\"high\":[12,13,12.5],\"low\":[9,10,11.5],"
                        + "\"close\":[11,12,12.2],\"volume\":[100,50,70]}]}}],\"error\":null}}"));

        var history = service.getHistory(HistoryRequest.builder(Symbol.of("AAPL"))
                .range(Range.ONE_DAY).interval(Interval.THIRTY_MINUTES).build());

        assertThat(server.takeRequest().getRequestUrl().queryParameter("interval")).isEqualTo("15m");
        assertThat(history.bars()).hasSize(2);
        assertThat(history.bars().getFirst().timestamp()).isEqualTo(Instant.ofEpochSecond(t0));
        assertThat(history.bars().getFirst().high()).isEqualByComparingTo("13");
        assertThat(history.bars().getFirst().close()).isEqualByComparingTo("12");
        assertThat(history.bars().getFirst().volume()).isEqualTo(150L);
    }

    @Test
    void thirtyMinuteErrorExplainsFetchedInterval() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":null,\"error\":{\"code\":\"Unprocessable Entity\","
                        + "\"description\":\"15m data not available for startTime=1 and endTime=2.\"}}}"));

        assertThatThrownBy(() -> service.getHistory(HistoryRequest.builder(Symbol.of("AAPL"))
                        .range(Range.ONE_DAY).interval(Interval.THIRTY_MINUTES).build()))
                .isInstanceOf(YFDataException.class)
                .hasMessage("Yahoo error for AAPL: 15m data not available for startTime=1 and endTime=2."
                        + " (30m resampled from 15m)");
    }
}
