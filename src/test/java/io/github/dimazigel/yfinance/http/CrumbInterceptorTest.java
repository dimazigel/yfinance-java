package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.valueobject.Crumb;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrumbInterceptorTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void appendsCrumbQueryParamAndUserAgent() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new UserAgentInterceptor("ua/9"))
                .addInterceptor(new CrumbInterceptor(() -> Crumb.of("XYZ")))
                .build();

        client.newCall(new Request.Builder().url(server.url("/v8/finance/chart/AAPL")).build())
                .execute()
                .close();

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().queryParameter("crumb")).isEqualTo("XYZ");
        assertThat(req.getHeader("User-Agent")).isEqualTo("ua/9");
    }

    @Test
    void proceedsWithoutCrumbWhenNoneAvailable() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new CrumbInterceptor(() -> null))
                .build();

        client.newCall(new Request.Builder().url(server.url("/v8/finance/chart/AAPL?crumb=stale")).build())
                .execute()
                .close();

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().queryParameter("crumb")).isNull();
    }
}
