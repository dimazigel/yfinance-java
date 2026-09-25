package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AdaptiveRateLimiterTest {

    private final AtomicLong nowNanos = new AtomicLong();
    private Instant now = Instant.parse("2026-05-30T10:00:00Z");
    private final List<Duration> sleeps = new ArrayList<>();

    @Test
    void startsWithNoDelay() throws Exception {
        var limiter = limiter(0.0, 0.0);

        limiter.beforeRequest();

        assertThat(sleeps).isEmpty();
        assertThat(limiter.currentDelay()).isZero();
    }

    @Test
    void first429AppliesInitialDelayToNextRequest() throws Exception {
        var limiter = limiter(0.0, 0.0);

        limiter.onResponse(429, null);
        limiter.beforeRequest();

        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(sleeps).containsExactly(Duration.ofMillis(500));
    }

    @Test
    void repeated429sIncreaseDelayMultiplicativelyUntilCap() {
        var limiter = limiter(0.0, 0.0);

        limiter.onResponse(429, null);
        limiter.onResponse(429, null);
        limiter.onResponse(429, null);
        limiter.onResponse(429, null);
        limiter.onResponse(429, null);

        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void retryAfterSecondsOverridesCalculatedDelay() throws Exception {
        var limiter = limiter(0.0, 0.0);

        limiter.onResponse(429, "3");
        limiter.beforeRequest();

        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofSeconds(3));
        assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void retryAfterHttpDateOverridesCalculatedDelay() throws Exception {
        var limiter = limiter(0.0, 0.0);

        limiter.onResponse(429, "Sat, 30 May 2026 10:00:04 GMT");
        limiter.beforeRequest();

        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofSeconds(4));
        assertThat(sleeps).containsExactly(Duration.ofSeconds(4));
    }

    @Test
    void retryAfterIsCappedAtMaximumDelay() {
        var limiter = limiter(0.0, 0.0);

        limiter.onResponse(429, "30");

        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void successfulResponsesGraduallyDecreaseDelayToZero() {
        var limiter = limiter(0.0, 0.0);
        limiter.onResponse(429, null);
        limiter.onResponse(429, null);
        limiter.onResponse(429, null);
        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofSeconds(2));

        limiter.onResponse(200, null);
        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofSeconds(1));

        limiter.onResponse(204, null);
        assertThat(limiter.currentDelay()).isZero();
    }

    @Test
    void nonSuccessNon429DoesNotDecreaseDelay() {
        var limiter = limiter(0.0, 0.0);
        limiter.onResponse(429, null);

        limiter.onResponse(500, null);

        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void jitterRandomizesScheduledWaitWithoutChangingBaseDelay() throws Exception {
        var limiter = limiter(0.5, 0.0);

        limiter.onResponse(429, null);
        limiter.beforeRequest();

        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(sleeps).containsExactly(Duration.ofMillis(250));
    }

    @Test
    void ignoresInvalidRetryAfterHeader() {
        var limiter = limiter(0.0, 0.0);

        limiter.onResponse(429, "not-a-date");

        assertThat(limiter.currentDelay()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void pacesEveryRequestWhileDegradedNotJustTheFirst() throws Exception {
        var limiter = limiter(0.0, 0.0);

        limiter.onResponse(429, null);   // delay 500ms
        limiter.beforeRequest();         // waits 500ms
        limiter.onResponse(500, null);   // server error: delay unchanged, still degraded
        limiter.beforeRequest();         // must wait ~500ms again, not burst

        assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofMillis(500));
    }

    @Test
    void stopsPacingOnceRecovered() throws Exception {
        var limiter = limiter(0.0, 0.0);

        limiter.onResponse(429, null);   // delay 500ms
        limiter.beforeRequest();         // waits 500ms
        limiter.onResponse(200, null);   // recovery: 500 * 0.5 <= initial -> delay 0
        limiter.beforeRequest();         // no wait

        assertThat(sleeps).containsExactly(Duration.ofMillis(500));
        assertThat(limiter.currentDelay()).isZero();
    }

    private AdaptiveRateLimiter limiter(double jitter, double random) {
        return new AdaptiveRateLimiter(
                new AdaptiveRateLimitConfig(
                        true, Duration.ofMillis(500), Duration.ofSeconds(4), 2.0, 0.5, jitter, 3),
                nowNanos::get,
                () -> now,
                delay -> {
                    sleeps.add(delay);
                    nowNanos.addAndGet(delay.toNanos());
                    now = now.plus(delay);
                },
                () -> random);
    }
}
