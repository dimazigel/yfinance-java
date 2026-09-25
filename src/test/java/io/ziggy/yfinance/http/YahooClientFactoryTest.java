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
        config = EndpointConfig.production().withHosts(base).withUserAgent("ua/1")
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

    @Test
    void authRetrySitsUpstreamOfRateLimiterAndCrumb() {
        // The rate limiter paces requests *before* sending while degraded; a retry issued below it
        // would skip that pacing. Pacing is timing-based, so the order is asserted structurally.
        var client = YahooClientFactory.apiClient(config, new InMemoryCookieJar(), () -> Crumb.of("c"), () -> {});

        var order = client.interceptors().stream().map(i -> i.getClass().getSimpleName()).toList();
        assertThat(order.indexOf("AuthRetryInterceptor"))
                .isLessThan(order.indexOf("AdaptiveRateLimitInterceptor"))
                .isLessThan(order.indexOf("CrumbInterceptor"));
    }

    @Test
    void authRetryFollowedBy429IsStillAbsorbedByTheRateLimiter() throws Exception {
        // 401 -> crumb refresh + retry; that retry meets a 429, which the rate limiter must absorb
        // (wait, retry) instead of returning it raw. Regression guard for the interceptor order.
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(200));
        var fast = new AdaptiveRateLimitConfig(true, Duration.ofMillis(1), Duration.ofMillis(5), 2.0, 0.5, 0.0, 3);
        var refreshes = new java.util.concurrent.atomic.AtomicInteger();
        var client = YahooClientFactory.apiClient(
                config.withAdaptiveRateLimit(fast), new InMemoryCookieJar(), () -> Crumb.of("c"), refreshes::incrementAndGet);

        try (var response = client.newCall(new Request.Builder().url(server.url("/x")).build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }
        assertThat(server.getRequestCount()).isEqualTo(3);
        assertThat(refreshes.get()).isEqualTo(1);
    }
}
