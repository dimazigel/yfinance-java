package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdaptiveRateLimitInterceptorTest {

    private MockWebServer server;
    private final AtomicLong nowNanos = new AtomicLong();
    private Instant now = Instant.parse("2026-05-30T10:00:00Z");
    private final List<Duration> sleeps = new ArrayList<>();
    private Runnable onSleep = () -> {};

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

    @Test
    void failsFastWhenThePendingWaitExceedsTheRemainingCallTimeout() throws Exception {
        // The first attempt took 25 s of a 30 s call budget and came back 429 Retry-After: 12; waiting
        // 12 s more can only end in "timeout" after the wait, so fail now, without sleeping.
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                nowNanos.addAndGet(Duration.ofSeconds(25).toNanos());
                return new MockResponse().setResponseCode(429).setHeader("Retry-After", "12");
            }
        });
        var limiter = limiter(3, Duration.ofSeconds(20));
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AdaptiveRateLimitInterceptor(limiter))
                .callTimeout(Duration.ofSeconds(30))
                .build();

        assertThatThrownBy(() -> client.newCall(new Request.Builder().url(server.url("/limited")).build()).execute())
                .isInstanceOf(InterruptedIOException.class)
                .hasMessage("rate-limit wait of 12000 ms exceeds the remaining call timeout");

        assertThat(sleeps).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void sleepsInSlicesAndStopsWhenTheCallIsCanceled() {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "3"));
        var limiter = limiter(3);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new AdaptiveRateLimitInterceptor(limiter)).build();
        Call call = client.newCall(new Request.Builder().url(server.url("/limited")).build());
        onSleep = call::cancel;   // the caller gives up during the first slice

        assertThatThrownBy(call::execute).isInstanceOf(InterruptedIOException.class).hasMessage("Canceled");

        assertThat(sleeps).as("one 1 s slice, then the cancel is noticed").containsExactly(Duration.ofSeconds(1));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void anUnboundedCallSleepsTheWholeWaitInSlices() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "3"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        var limiter = limiter(3);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new AdaptiveRateLimitInterceptor(limiter)).build();

        try (var response = client.newCall(new Request.Builder().url(server.url("/limited")).build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }

        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void aBurstOfConcurrent429sRaisesThePaceOnce() throws Exception {
        var bothArrived = new CountDownLatch(2);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                bothArrived.countDown();
                bothArrived.await(5, TimeUnit.SECONDS);   // both requests are in flight before either answer
                nowNanos.addAndGet(Duration.ofMillis(1).toNanos());
                return new MockResponse().setResponseCode(429);
            }
        });
        var limiter = limiter(1);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new AdaptiveRateLimitInterceptor(limiter)).build();
        var request = new Request.Builder().url(server.url("/limited")).build();

        try (var pool = Executors.newFixedThreadPool(2)) {
            var codes = pool.invokeAll(List.of(
                    () -> { try (var r = client.newCall(request).execute()) { return r.code(); } },
                    () -> { try (var r = client.newCall(request).execute()) { return r.code(); } }));
            assertThat(codes.get(0).get()).isEqualTo(429);
            assertThat(codes.get(1).get()).isEqualTo(429);
        }

        assertThat(limiter.currentDelay()).as("one event, one doubling: 0 -> 500 ms, not 1 s").isEqualTo(Duration.ofMillis(500));
    }

    private AdaptiveRateLimiter limiter(int maxAttempts) {
        return limiter(maxAttempts, Duration.ofSeconds(4));
    }

    private AdaptiveRateLimiter limiter(int maxAttempts, Duration maxDelay) {
        return new AdaptiveRateLimiter(
                new AdaptiveRateLimitConfig(
                        true, Duration.ofMillis(500), maxDelay, 2.0, 0.5, 0.0, maxAttempts),
                nowNanos::get,
                () -> now,
                delay -> {
                    sleeps.add(delay);
                    nowNanos.addAndGet(delay.toNanos());
                    now = now.plus(delay);
                    onSleep.run();
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
