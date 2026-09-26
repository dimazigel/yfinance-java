package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdaptiveRateLimitInterceptorTest {

    private MockWebServer server;
    private final AtomicLong nowNanos = new AtomicLong();
    private Instant now = Instant.parse("2026-05-30T10:00:00Z");
    private final List<Duration> sleeps = new ArrayList<>();

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
    void singleAttemptConfigDoesNotRetry() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "2"));

        var limiter = limiter(1);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AdaptiveRateLimitInterceptor(limiter))
                .build();

        try (var response = client.newCall(new Request.Builder().url(server.url("/limited")).build()).execute()) {
            assertThat(response.code()).isEqualTo(429);
        }

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retries429UpToMaxAttemptsAndSucceeds() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        var limiter = limiter(3);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AdaptiveRateLimitInterceptor(limiter))
                .build();

        try (var response = client.newCall(new Request.Builder().url(server.url("/limited")).build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }

        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(sleeps).containsExactly(Duration.ofMillis(500)); // waited before the retry
    }

    @Test
    void returnsLast429WhenAttemptsExhausted() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));

        var limiter = limiter(2);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AdaptiveRateLimitInterceptor(limiter))
                .build();

        try (var response = client.newCall(new Request.Builder().url(server.url("/limited")).build()).execute()) {
            assertThat(response.code()).isEqualTo(429);
        }

        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofSeconds(1)); // 500ms * 2
    }

    @Test
    void delaysFutureCallsAfter429AndRecoversAfterSuccess() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        var limiter = limiter(1);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AdaptiveRateLimitInterceptor(limiter))
                .build();

        client.newCall(new Request.Builder().url(server.url("/first")).build()).execute().close();
        client.newCall(new Request.Builder().url(server.url("/second")).build()).execute().close();
        client.newCall(new Request.Builder().url(server.url("/third")).build()).execute().close();

        assertThat(sleeps).containsExactly(Duration.ofMillis(500));
        assertThat(limiter.currentDelay()).isZero();
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    private AdaptiveRateLimiter limiter(int maxAttempts) {
        return new AdaptiveRateLimiter(
                new AdaptiveRateLimitConfig(
                        true, Duration.ofMillis(500), Duration.ofSeconds(4), 2.0, 0.5, 0.0, maxAttempts),
                nowNanos::get,
                () -> now,
                delay -> {
                    sleeps.add(delay);
                    nowNanos.addAndGet(delay.toNanos());
                    now = now.plus(delay);
                },
                () -> 0.0);
    }

    @Test
    void warnsOnceWhenStillRateLimitedAfterAllAttempts() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));
        var client = new OkHttpClient.Builder().addInterceptor(new AdaptiveRateLimitInterceptor(limiter(2))).build();

        try (var log = io.github.dimazigel.yfinance.testsupport.LogCapture.of(AdaptiveRateLimitInterceptor.class)) {
            client.newCall(new Request.Builder().url(server.url("/limited")).build()).execute().close();

            assertThat(log.messages(ch.qos.logback.classic.Level.WARN)).singleElement().satisfies(m ->
                    assertThat(m).startsWith("Giving up on /limited after 2 attempts: still rate limited (HTTP 429)"));
        }
    }
}
