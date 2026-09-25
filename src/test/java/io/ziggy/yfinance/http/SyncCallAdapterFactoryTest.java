package io.ziggy.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ziggy.yfinance.api.ChartApi;
import io.ziggy.yfinance.exception.YFDataException;
import io.ziggy.yfinance.exception.YFRateLimitException;
import io.ziggy.yfinance.testsupport.Fixtures;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyncCallAdapterFactoryTest {

    private MockWebServer server;
    private ChartApi api;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        api = Fixtures.api(server, ChartApi.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void httpErrorMessageIncludesStatusAndBody() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("upstream boom details"));

        assertThatThrownBy(() -> api.chart("AAPL", "1d", "1mo", null, null, false, null))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("500")
                .hasMessageContaining("/v8/finance/chart/AAPL") // which request failed
                .hasMessageContaining("upstream boom details");
    }

    @Test
    void httpErrorWithYahooErrorEnvelopeSurfacesItsDescription() {
        server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json")
                .setBody("{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\","
                        + "\"description\":\"No data found, symbol may be delisted\"}}}"));

        assertThatThrownBy(() -> api.chart("NOPE", "1d", "1mo", null, null, false, null))
                .isInstanceOf(YFDataException.class)
                .hasMessage("Yahoo Finance returned HTTP 404 for /v8/finance/chart/NOPE: No data found, symbol may be delisted");
    }

    @Test
    void rateLimitCarriesRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "12").setBody("slow down"));

        assertThatThrownBy(() -> api.chart("AAPL", "1d", "1mo", null, null, false, null))
                .isInstanceOf(YFRateLimitException.class)
                .satisfies(e -> assertThat(((YFRateLimitException) e).retryAfter())
                        .contains(Duration.ofSeconds(12)));
    }

    @Test
    void rateLimitWithoutHeaderHasEmptyRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("slow down"));

        assertThatThrownBy(() -> api.chart("AAPL", "1d", "1mo", null, null, false, null))
                .isInstanceOf(YFRateLimitException.class)
                .satisfies(e -> assertThat(((YFRateLimitException) e).retryAfter()).isEmpty());
    }
}
