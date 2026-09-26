package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.api.ChartApi;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFRateLimitException;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
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

    @Test
    void htmlErrorPagesAreSummarisedNotQuoted() {
        var html = "<!doctype html public \"-//W3C//DTD HTML 4.01//EN\"><html><head><title>Yahoo! - Error report</title></head></html>";
        server.enqueue(new MockResponse().setResponseCode(500).setBody(html));

        assertThatThrownBy(() -> api.chart("AAPL", "1d", "1mo", null, null, false, null))
                .isInstanceOf(YFDataException.class)
                .hasMessage("Yahoo Finance returned HTTP 500 for /v8/finance/chart/AAPL: HTML error page (" + html.length() + " bytes)");
    }

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
}
