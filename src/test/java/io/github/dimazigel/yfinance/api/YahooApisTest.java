package io.github.dimazigel.yfinance.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The seven Feign interfaces send the request shapes Yahoo expects: path variables, query names, omitted nulls, and the query2 host for fundamentals. */
class YahooApisTest {

    private MockWebServer server;
    private YahooApis apis;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        apis = Fixtures.apis(server);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private HttpUrl sent() throws InterruptedException {
        return server.url(Objects.requireNonNull(server.takeRequest().getPath()));
    }

    @Test
    void chartWithRangeSendsNoPeriods() throws Exception {
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));
        apis.chart().chart("AAPL", "1d", "1mo", null, null, false, "div,splits");
        HttpUrl url = sent();
        assertThat(url.encodedPath()).isEqualTo("/v8/finance/chart/AAPL");
        assertThat(url.queryParameterNames()).containsExactlyInAnyOrder("interval", "range", "includePrePost", "events");
        assertThat(url.queryParameter("events")).isEqualTo("div,splits");
    }

    @Test
    void chartWithPeriodsSendsNoRange() throws Exception {   // Review Focus 1
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));
        apis.chart().chart("^GSPC", "1d", null, 1700000000L, 1700086400L, true, null);
        HttpUrl url = sent();
        assertThat(url.pathSegments()).containsExactly("v8", "finance", "chart", "^GSPC");
        assertThat(url.queryParameterNames()).containsExactlyInAnyOrder("interval", "period1", "period2", "includePrePost");
        assertThat(url.queryParameter("period2")).isEqualTo("1700086400");
    }

    @Test
    void quoteRowsAndModulesKeepTheirQueryShape() throws Exception {
        server.enqueue(Fixtures.jsonResponse("instruments/v7_AAPL.json"));
        apis.quote().quoteRows("AAPL,EURUSD=X", false);
        HttpUrl v7 = sent();
        assertThat(v7.encodedPath()).isEqualTo("/v7/finance/quote");
        assertThat(v7.queryParameter("symbols")).isEqualTo("AAPL,EURUSD=X");
        assertThat(v7.queryParameter("formatted")).isEqualTo("false");

        server.enqueue(Fixtures.jsonResponse("instruments/qs_BTC_USD.json"));
        apis.quoteSummary().modules("BTC-USD", "price,summaryDetail", false, "finance.yahoo.com");
        HttpUrl qs = sent();
        assertThat(qs.pathSegments()).containsExactly("v10", "finance", "quoteSummary", "BTC-USD");
        assertThat(qs.queryParameter("modules")).isEqualTo("price,summaryDetail");
        assertThat(qs.queryParameter("corsDomain")).isEqualTo("finance.yahoo.com");
    }

    @Test
    void optionsOmitsDateWhenAbsent() throws Exception {
        server.enqueue(Fixtures.jsonResponse("options/options_AAPL.json"));
        apis.options().options("AAPL", null);
        assertThat(sent().queryParameterNames()).isEmpty();

        server.enqueue(Fixtures.jsonResponse("options/options_AAPL_1790726400.json"));
        apis.options().options("AAPL", 1790726400L);
        assertThat(sent().queryParameter("date")).isEqualTo("1790726400");
    }

    @Test
    void fundamentalsGoToTheSecondHost() throws Exception {
        var second = new MockWebServer();
        second.start();
        try {
            var config = EndpointConfig.production().withHosts(server.url("/"), second.url("/"), server.url("/"));
            var split = YahooApis.create(config, new OkHttpClient());
            second.enqueue(Fixtures.jsonResponse("timeseries_income_annual.json"));
            split.fundamentals().timeseries("AAPL", "annualTotalRevenue", 1600000000L, 1700000000L);
            var request = second.takeRequest();
            assertThat(request.getPath()).startsWith("/ws/fundamentals-timeseries/v1/finance/timeseries/AAPL?");
            assertThat(server.getRequestCount()).isZero();
        } finally {
            second.shutdown();
        }
    }
}
