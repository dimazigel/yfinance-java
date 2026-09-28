package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.auth.CrumbStore;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.valueobject.Crumb;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
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
        assertThat(order.indexOf("LogContextInterceptor")).isEqualTo(1); // right after User-Agent: every line below sees yf.endpoint
        assertThat(order.indexOf("AuthRetryInterceptor"))
                .isLessThan(order.indexOf("TransientErrorRetryInterceptor"));
        assertThat(order.indexOf("TransientErrorRetryInterceptor"))
                .isLessThan(order.indexOf("AdaptiveRateLimitInterceptor")); // 5xx retries are paced too
        assertThat(order.indexOf("AdaptiveRateLimitInterceptor"))
                .isLessThan(order.indexOf("RequestLogInterceptor")); // logs every physical attempt...
        assertThat(order.indexOf("RequestLogInterceptor"))
                .isLessThan(order.indexOf("CrumbInterceptor"));      // ...before the crumb is attached
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

    @Test
    void transientServerErrorIsRetriedInsideTheApiClient() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("<html>Yahoo! - Error report</html>"));
        server.enqueue(new MockResponse().setResponseCode(200));
        var fast = config.withTransientRetry(new RetryConfig(2, Duration.ofMillis(1), Duration.ofMillis(1)));
        var client = YahooClientFactory.apiClient(fast, new InMemoryCookieJar(), () -> Crumb.of("c"), () -> {});

        try (var response = client.newCall(new Request.Builder().url(server.url("/x")).build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void dispatcherIsSizedByTheFanOutConcurrency() {
        // Only enqueue()d calls through the customizer's client are governed by these limits; the
        // library's own calls are synchronous and bounded by the fan-out semaphore alone.
        var twelve = YahooClientFactory.newDispatcher(config.withFanOutConcurrency(12));
        assertThat(twelve.getMaxRequestsPerHost()).isEqualTo(12);
        assertThat(twelve.getMaxRequests()).isEqualTo(12);

        var four = YahooClientFactory.newDispatcher(config.withFanOutConcurrency(4));
        assertThat(four.getMaxRequestsPerHost()).as("never below OkHttp's default of 5").isEqualTo(5);

        var client = YahooClientFactory.apiClient(config.withFanOutConcurrency(12));
        assertThat(client.dispatcher().getMaxRequestsPerHost()).isEqualTo(12);
    }

    @Test
    void rateLimiterMaxDelayIsClampedToTheCallTimeoutWithOneWarning() {   // review, important 1
        var shortTimeout = EndpointConfig.production().withHosts(server.url("/")).withCallTimeout(Duration.ofSeconds(5));   // default maxDelay 10 s
        try (var log = LogCapture.of(YahooClientFactory.class)) {
            var clamped = YahooClientFactory.newRateLimiter(shortTimeout);
            assertThat(clamped.limiter().config().maxDelay()).isEqualTo(Duration.ofSeconds(5));
            assertThat(clamped.limiter().config().initialDelay()).as("the rest of the tuning is untouched").isEqualTo(Duration.ofMillis(500));
            assertThat(log.messages(Level.WARN)).singleElement().satisfies(m ->
                    assertThat(m).isEqualTo("rate-limit maxDelay PT10S clamped to callTimeout PT5S"));

            assertThat(YahooClientFactory.newRateLimiter(shortTimeout.withCallTimeout(Duration.ofSeconds(30))).limiter().config().maxDelay())
                    .isEqualTo(Duration.ofSeconds(10));
            assertThat(YahooClientFactory.newRateLimiter(shortTimeout.withCallTimeout(Duration.ZERO)).limiter().config().maxDelay())
                    .as("no call timeout: nothing to clamp to").isEqualTo(Duration.ofSeconds(10));
            assertThat(YahooClientFactory.newRateLimiter(shortTimeout.withAdaptiveRateLimit(AdaptiveRateLimitConfig.disabled()))
                    .limiter().config().enabled()).isFalse();
            assertThat(log.messages(Level.WARN)).as("only the clamped case warns").hasSize(1);
        }
        var yf = YahooClientFactory.apiClient(shortTimeout);   // the convenience overloads clamp too
        assertThat(((AdaptiveRateLimitInterceptor) yf.interceptors().stream()
                .filter(i -> i instanceof AdaptiveRateLimitInterceptor).findFirst().orElseThrow()).limiter().config().maxDelay())
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void aColdHandshakeInsideAPacedCallIsNotPacedAgain() throws Exception {   // review, important 4
        // While degraded, the api request waits its slot; the handshake it triggers (cookie + crumb on
        // the base client, same limiter, same thread) must ride inside that slot, not wait two more.
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return switch (request.getRequestUrl().encodedPath()) {
                    case "/v1/test/getcrumb" -> new MockResponse().setResponseCode(200).setBody("fresh");
                    case "/data" -> new MockResponse().setResponseCode(200).setBody("{}");
                    default -> new MockResponse().setResponseCode(404);   // the cookie seed
                };
            }
        });
        var sleeps = new ArrayList<Duration>();
        var nowNanos = new AtomicLong();
        var limiter = new AdaptiveRateLimiter(
                new AdaptiveRateLimitConfig(true, Duration.ofMillis(500), Duration.ofSeconds(4), 2.0, 0.5, 0.0, 3),
                nowNanos::get, Instant::now, d -> { sleeps.add(d); nowNanos.addAndGet(d.toNanos()); }, () -> 0.0);
        limiter.onResponse(429, null);   // degraded: pace 500 ms
        var shared = new AdaptiveRateLimitInterceptor(limiter);
        var cookieJar = new InMemoryCookieJar();
        var dispatcher = new Dispatcher();
        var pool = new ConnectionPool();
        var store = new CrumbStore(YahooClientFactory.baseClient(config, cookieJar, shared, dispatcher, pool), config);
        var api = YahooClientFactory.apiClient(
                config, cookieJar, () -> store.tryGetCrumb().orElse(null), store::invalidate, shared, dispatcher, pool);

        try (var response = api.newCall(new Request.Builder().url(server.url("/data")).build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }

        assertThat(server.getRequestCount()).as("cookie seed, crumb, data").isEqualTo(3);
        assertThat(server.takeRequest().getPath()).isEqualTo("/");
        assertThat(server.takeRequest().getPath()).isEqualTo("/v1/test/getcrumb");
        assertThat(server.takeRequest().getRequestUrl().queryParameter("crumb")).isEqualTo("fresh");
        assertThat(sleeps).as("one paced wait for the api request; none for the nested handshake").containsExactly(Duration.ofMillis(500));
    }

    @Test
    void handshakeClientSharesTheApiClientsRateLimiter() {
        var cookieJar = new InMemoryCookieJar();
        var limiter = new AdaptiveRateLimitInterceptor(config.adaptiveRateLimit());
        var dispatcher = new Dispatcher();
        var pool = new ConnectionPool();

        var base = YahooClientFactory.baseClient(config, cookieJar, limiter, dispatcher, pool);
        var api = YahooClientFactory.apiClient(
                config, cookieJar, () -> Crumb.of("c"), rejected -> { }, limiter, dispatcher, pool);

        assertThat(base.interceptors()).as("the same limiter instance paces the handshake").contains(limiter);
        assertThat(api.interceptors()).contains(limiter);
        var order = base.interceptors().stream().map(i -> i.getClass().getSimpleName()).toList();
        assertThat(order.indexOf("LogContextInterceptor")).isLessThan(order.indexOf("AdaptiveRateLimitInterceptor"));
        assertThat(order.indexOf("AdaptiveRateLimitInterceptor")).isLessThan(order.indexOf("RequestLogInterceptor"));
        assertThat(order).doesNotContain("CrumbInterceptor", "AuthRetryInterceptor");
    }
}
