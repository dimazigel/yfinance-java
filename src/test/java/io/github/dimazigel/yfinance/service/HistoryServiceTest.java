package io.github.dimazigel.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.api.ChartApi;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.market.PriceHistory;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

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
        assertThat(first.adjClose().orElseThrow()).isEqualByComparingTo("187.8");
        assertThat(first.volume()).contains(50_000_000L);

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
        assertThat(meta.currency().code()).isEqualTo("USD");
        assertThat(meta.timezone()).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(meta.symbol()).isEqualTo(Symbol.of("AAPL"));
        assertThat(meta.regularMarketPrice()).isEqualByComparingTo("190.5");
    }

    @Test
    void metadataIsNonNull() {
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));

        var meta = service.getHistory(
                HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_MONTH).build()).metadata();

        assertThat(meta.currency().code()).isEqualTo("USD");
        assertThat(meta.regularMarketTime()).isEqualTo(Instant.ofEpochSecond(1700172800));
        assertThat(meta.currentTradingPeriod().regular().start()).isEqualTo(Instant.ofEpochSecond(1700146200));
    }

    @Test
    void incompleteMetadataThrows() {
        // Every required field except currency is present.
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":{\"symbol\":\"AAPL\","
                        + "\"exchangeName\":\"NMS\",\"fullExchangeName\":\"NasdaqGS\",\"instrumentType\":\"EQUITY\","
                        + "\"firstTradeDate\":345479400,\"regularMarketTime\":1700172800,"
                        + "\"exchangeTimezoneName\":\"America/New_York\",\"regularMarketPrice\":190.5,"
                        + "\"chartPreviousClose\":186.9,\"priceHint\":2,\"hasPrePostMarketData\":true,"
                        + "\"currentTradingPeriod\":{"
                        + "\"pre\":{\"start\":1700126400,\"end\":1700146200},"
                        + "\"regular\":{\"start\":1700146200,\"end\":1700169600},"
                        + "\"post\":{\"start\":1700169600,\"end\":1700184000}}},"
                        + "\"timestamp\":[1700000000],"
                        + "\"indicators\":{\"quote\":[{\"open\":[1.0],\"high\":[1.2],\"low\":[0.9],\"close\":[1.1]}]}}],"
                        + "\"error\":null}}"));

        assertThatThrownBy(() -> service.getHistory(
                        HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_DAY).build()))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void unknownCurrencyCodeIsPreservedRatherThanDropped() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":" + fullMeta("XYZ", "AAPL", "America/New_York") + ","
                        + "\"timestamp\":[1700000000],"
                        + "\"indicators\":{\"quote\":[{\"open\":[1.0],\"high\":[1.2],\"low\":[0.9],\"close\":[1.1]}]}}],"
                        + "\"error\":null}}"));

        var history = service.getHistory(
                HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_DAY).build());

        assertThat(history.metadata().currency().code()).isEqualTo("XYZ");
        assertThat(history.metadata().currency().iso()).isEmpty();
    }

    @Test
    void invalidTimezoneMakesMetadataIncomplete() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":" + fullMeta("USD", "AAPL", "Not/A_Zone") + ","
                        + "\"timestamp\":[1700000000],"
                        + "\"indicators\":{\"quote\":[{\"open\":[1.0],\"high\":[1.2],\"low\":[0.9],\"close\":[1.1]}]}}],"
                        + "\"error\":null}}"));

        assertThatThrownBy(() -> service.getHistory(
                        HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_DAY).build()))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("timezone");
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
    void openEndedPeriodDefaultsEndToNow() throws Exception {
        // Yahoo rejects period1 without period2 ("start date cannot be after end date ... endDate = -1"),
        // so an open-ended window must send the current time, like Python yfinance does.
        var fixedNow = Instant.ofEpochSecond(1_800_000_000L);
        var clocked = new HistoryService(Fixtures.api(server, ChartApi.class), Clock.fixed(fixedNow, ZoneOffset.UTC));
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));

        clocked.getHistory(HistoryRequest.builder(Symbol.of("AAPL"))
                .interval(Interval.ONE_DAY)
                .period(Instant.ofEpochSecond(1000), null)
                .build());

        var url = server.takeRequest().getRequestUrl();
        assertThat(url.queryParameter("period1")).isEqualTo("1000");
        assertThat(url.queryParameter("period2")).isEqualTo("1800000000");
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
    void incompleteChartEventsAreDroppedAndLogged() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":" + fullMeta("USD", "AAPL", "America/New_York") + ","
                        + "\"timestamp\":[1700000000],"
                        + "\"events\":{\"dividends\":{\"1699000000\":{\"date\":1699000000},"
                        + "\"1698000000\":{\"amount\":0.24,\"date\":1698000000}}},"
                        + "\"indicators\":{\"quote\":[{\"open\":[187.0],\"high\":[189.0],\"low\":[186.5],\"close\":[188.0],\"volume\":[1]}]}}],\"error\":null}}"));

        try (var log = LogCapture.ofLibrary()) {
            var history = service.getHistory(
                    HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_DAY).build());

            assertThat(history.dividends()).hasSize(1);
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m)
                    .isEqualTo("Dropped a dividend event without a complete date and value (date=1699000000)"));
        }
    }

    @Test
    void barsWithAnyNullOhlcAreDropped() {
        // A row with just one missing OHLC value (here: open) is dropped, same as an all-null row.
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":" + fullMeta("USD", "AAPL", "America/New_York") + ","
                        + "\"timestamp\":[1700000000,1700000060,1700000120],"
                        + "\"indicators\":{\"quote\":[{"
                        + "\"open\":[187.0,null,189.1],"
                        + "\"high\":[189.0,190.0,191.2],"
                        + "\"low\":[186.5,187.9,188.0],"
                        + "\"close\":[188.0,189.2,190.5],"
                        + "\"volume\":[50000000,48000000,52000000]}]}}],\"error\":null}}"));

        try (var log = LogCapture.ofLibrary()) {
            var history = service.getHistory(
                    HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_DAY).build());

            assertThat(history.bars()).hasSize(2);
            assertThat(log.messages(Level.DEBUG))
                    .anySatisfy(m -> assertThat(m).isEqualTo("Dropped 1 of 3 bars without a complete OHLC"));
        }
    }

    @Test
    void skipsAllNullBarsAndKeepsMissingVolumeAsNull() {
        // Yahoo pads intraday responses with all-null rows (halts, pre-open) and sometimes
        // omits volume. All-null rows must be dropped; missing volume must NOT become 0.
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":" + fullMeta("USD", "AAPL", "America/New_York") + ","
                        + "\"timestamp\":[1700000000,1700000060,1700000120],"
                        + "\"indicators\":{\"quote\":[{"
                        + "\"open\":[187.0,null,189.1],"
                        + "\"high\":[189.0,null,191.2],"
                        + "\"low\":[186.5,null,188.0],"
                        + "\"close\":[188.0,null,190.5],"
                        + "\"volume\":[50000000,null,null]}]}}],\"error\":null}}"));

        var history = service.getHistory(
                HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_DAY).build());

        assertThat(history.bars()).hasSize(2); // all-null row dropped
        assertThat(history.bars().getFirst().volume()).contains(50_000_000L);
        assertThat(history.bars().getLast().volume()).isEmpty(); // missing, not zero
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
                "{\"chart\":{\"result\":[{\"meta\":" + fullMeta("USD", "AAPL", "America/New_York") + ","
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
        assertThat(history.bars().getFirst().volume()).contains(150L);
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

    @Test
    void blankMetaSymbolFallsBackToRequestedSymbol() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":" + fullMeta("USD", "  ", "America/New_York") + ","
                        + "\"timestamp\":[1700000000],"
                        + "\"indicators\":{\"quote\":[{\"open\":[1.0],\"high\":[2.0],\"low\":[0.5],\"close\":[1.5]}]}}],"
                        + "\"error\":null}}"));

        var history = service.getHistory(HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_DAY).build());

        assertThat(history.metadata().symbol()).isEqualTo(Symbol.of("AAPL"));
    }

    @Test
    void metadataCarriesGranularityRangesAndTradingPeriods() {
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));

        var meta = service.getHistory(
                HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_MONTH).build()).metadata();

        assertThat(meta.regularMarketTime()).isEqualTo(Instant.ofEpochSecond(1700172800));
        assertThat(meta.priceHint()).isEqualTo(2);
        assertThat(meta.hasPrePostMarketData()).isTrue();
        assertThat(meta.dataGranularity()).contains(Interval.ONE_DAY);
        assertThat(meta.validRanges()).startsWith(Range.ONE_DAY, Range.FIVE_DAYS).endsWith(Range.MAX)
                .hasSize(11); // the unknown "bogus-range" is dropped, not fatal
        var periods = meta.currentTradingPeriod();
        assertThat(periods.regular().start()).isEqualTo(Instant.ofEpochSecond(1700146200));
        assertThat(periods.regular().end()).isEqualTo(Instant.ofEpochSecond(1700169600));
        assertThat(periods.pre().end()).isEqualTo(periods.regular().start());
        assertThat(periods.post().start()).isEqualTo(periods.regular().end());
    }

    @Test
    void unknownRangeIsDroppedAndLoggedAtDebug() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"chart\":{\"result\":[{\"meta\":" + fullMeta("GBp", "BP.L", "Europe/London", "[\"1d\",\"bogus\"]") + ","
                        + "\"timestamp\":[1700000000],"
                        + "\"indicators\":{\"quote\":[{\"open\":[1.0],\"high\":[1.2],\"low\":[0.9],\"close\":[1.1]}]}}],"
                        + "\"error\":null}}"));

        try (var log = LogCapture.ofLibrary()) {
            var history = service.getHistory(HistoryRequest.builder(Symbol.of("BP.L")).range(Range.ONE_DAY).build());

            assertThat(history.metadata().validRanges()).containsExactly(Range.ONE_DAY);
            assertThat(log.messages(Level.DEBUG))
                    .anySatisfy(m -> assertThat(m).isEqualTo("Unknown range \"bogus\"; ignored"));
        }
    }

    @Test
    void requestsRunInsideALogContextScope() throws Exception {
        var seen = new HashMap<String, String>();
        var api = Fixtures.apis(server, chain -> {
            seen.putAll(MDC.getCopyOfContextMap());
            return chain.proceed(chain.request());
        }).chart();
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));

        new HistoryService(api).getHistory(HistoryRequest.builder(Symbol.of("AAPL")).range(Range.ONE_MONTH).build());

        assertThat(seen).containsEntry("yf.op", "history").containsEntry("yf.symbol", "AAPL");
        assertThat(MDC.get("yf.op")).isNull(); // cleared once the call returns
    }

    /** A complete, valid chart {@code meta} object with an empty {@code validRanges}. */
    private static String fullMeta(String currency, String symbol, String timezone) {
        return fullMeta(currency, symbol, timezone, "[]");
    }

    /** As above, but with an explicit {@code validRanges} JSON array. */
    private static String fullMeta(String currency, String symbol, String timezone, String validRangesJson) {
        return "{\"currency\":\"" + currency + "\",\"symbol\":\"" + symbol + "\","
                + "\"exchangeName\":\"NMS\",\"fullExchangeName\":\"NasdaqGS\",\"instrumentType\":\"EQUITY\","
                + "\"firstTradeDate\":345479400,\"regularMarketTime\":1700172800,"
                + "\"exchangeTimezoneName\":\"" + timezone + "\",\"regularMarketPrice\":190.5,"
                + "\"chartPreviousClose\":186.9,\"priceHint\":2,\"hasPrePostMarketData\":true,"
                + "\"validRanges\":" + validRangesJson + ","
                + "\"currentTradingPeriod\":{"
                + "\"pre\":{\"start\":1700126400,\"end\":1700146200},"
                + "\"regular\":{\"start\":1700146200,\"end\":1700169600},"
                + "\"post\":{\"start\":1700169600,\"end\":1700184000}}}";
    }
}
