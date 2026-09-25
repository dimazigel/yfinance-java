package io.ziggy.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.ziggy.yfinance.valueobject.Crumb;
import java.time.Duration;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YahooClientFactoryTest {

    private MockWebServer server;
    private EndpointConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        HttpUrl base = server.url("/");
        config = new EndpointConfig(base, base, base, "ua/1")
                .withClientCustomizer(b -> b
                        .addInterceptor(chain -> chain.proceed(
                                chain.request().newBuilder().header("X-Custom", "yes").build()))
                        .callTimeout(Duration.ofSeconds(7)));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void customizerAppliesToBaseClient() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        var client = YahooClientFactory.baseClient(config);

        client.newCall(new Request.Builder().url(server.url("/x")).build()).execute().close();

        RecordedRequest req = server.takeRequest();
        assertThat(req.getHeader("X-Custom")).isEqualTo("yes");
        assertThat(req.getHeader("User-Agent")).isEqualTo("ua/1"); // library interceptors still present
        assertThat(client.callTimeoutMillis()).isEqualTo(7_000); // customizer runs last, so it can override
    }

    @Test
    void customizerAppliesToApiClientAlongsideCrumb() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        var client = YahooClientFactory.apiClient(
                config, new InMemoryCookieJar(), () -> Crumb.of("c1"), () -> {});

        client.newCall(new Request.Builder().url(server.url("/x")).build()).execute().close();

        RecordedRequest req = server.takeRequest();
        assertThat(req.getHeader("X-Custom")).isEqualTo("yes");
        assertThat(req.getRequestUrl().queryParameter("crumb")).isEqualTo("c1");
    }
}
