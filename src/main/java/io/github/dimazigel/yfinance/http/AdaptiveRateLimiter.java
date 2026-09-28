package io.github.dimazigel.yfinance.http;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Shared adaptive throttle for a Yahoo client. It reacts quickly to HTTP 429 and recovers
 * conservatively after successful responses. One instance is shared by every interceptor chain of a
 * client (the handshake client included), so all of them pace against the same state.
 */
final class AdaptiveRateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(AdaptiveRateLimiter.class);

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration delay) throws InterruptedException;
    }

    private final AdaptiveRateLimitConfig config;
    private final LongSupplier nanoTime;
    private final Supplier<Instant> instantNow;
    private final Sleeper sleeper;
    private final DoubleSupplier random;
    private final long initialDelayNanos;
    private final long maxDelayNanos;

    private long currentDelayNanos;
    private long nextAllowedAtNanos;
    /** When the pace was last raised; a 429 for a request sent before then belongs to the same burst. */
    private long lastIncreaseNanos = Long.MIN_VALUE;

    AdaptiveRateLimiter(AdaptiveRateLimitConfig config) {
        this(
                config,
                System::nanoTime,
                Instant::now,
                AdaptiveRateLimiter::sleepThread,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    AdaptiveRateLimiter(
            AdaptiveRateLimitConfig config,
            LongSupplier nanoTime,
            Supplier<Instant> instantNow,
            Sleeper sleeper,
            DoubleSupplier random) {
        this.config = Objects.requireNonNull(config, "config");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.instantNow = Objects.requireNonNull(instantNow, "instantNow");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.random = Objects.requireNonNull(random, "random");
        this.initialDelayNanos = config.initialDelay().toNanos();
        this.maxDelayNanos = config.maxDelay().toNanos();
    }

    /** The limiter's monotonic clock, so callers measure elapsed time on the same clock (injectable in tests). */
    long nanoTime() {
        return nanoTime.getAsLong();
    }

    /**
     * How long the caller must still wait before sending, or zero when it may send now — in which
     * case the next slot has been reserved: while degraded, every request is paced by the current
     * delay, not just the first one after a 429, so traffic does not burst straight back into the
     * limit. Ask again after waiting; another thread may have taken the slot meanwhile.
     */
    synchronized Duration pendingWait() {
        if (!config.enabled()) {
            return Duration.ZERO;
        }
        long remainingNanos = nextAllowedAtNanos - nanoTime.getAsLong();
        if (remainingNanos <= 0L) {
            if (currentDelayNanos > 0L) {
                nextAllowedAtNanos = nanoTime.getAsLong() + jittered(currentDelayNanos);
            }
            return Duration.ZERO;
        }
        return Duration.ofNanos(remainingNanos);
    }

    /** Waits (through the sleeper) until {@link #pendingWait()} is zero. No call budget: see the interceptor for that. */
    void beforeRequest() throws InterruptedException {
        Duration wait = pendingWait();
        while (!wait.isZero()) {
            LOG.atDebug().addKeyValue("delayMs", wait.toMillis()).log("Rate limited; waiting {} ms before next request", wait.toMillis());
            sleeper.sleep(wait);
            wait = pendingWait();
        }
    }

    /** Sleeps through the injected sleeper, so the interceptor's slices are testable with a fake clock. */
    void sleep(Duration delay) throws InterruptedException {
        sleeper.sleep(delay);
    }

    /** Total attempts the interceptor may make per request (1 when throttling is disabled). */
    int maxAttemptsPerRequest() {
        return config.enabled() ? config.maxAttempts() : 1;
    }

    /** As {@link #onResponse(int, String, long)} for a request sent just now. */
    synchronized void onResponse(int code, @Nullable String retryAfter) {
        onResponse(code, retryAfter, nanoTime.getAsLong());
    }

    /**
     * Feeds back a response for a request sent at {@code sentNanos}. A 429 for a request that was
     * already in flight when the pace was last raised is part of the same burst: it defers the next
     * slot but does not raise the pace again, so N concurrent 429s cost one doubling, not N.
     */
    synchronized void onResponse(int code, @Nullable String retryAfter, long sentNanos) {
        if (!config.enabled()) {
            return;
        }
        if (code == 429) {
            if (sentNanos < lastIncreaseNanos) {
                deferWithoutIncrease();
            } else {
                increaseDelay(retryAfter);
                lastIncreaseNanos = nanoTime.getAsLong();
            }
        } else if (code >= 200 && code < 400) {
            decreaseDelay();
        }
    }

    synchronized Duration currentDelay() {
        return Duration.ofNanos(currentDelayNanos);
    }

    private void deferWithoutIncrease() {
        long paceMs = Duration.ofNanos(currentDelayNanos).toMillis();
        LOG.atDebug()
                .addKeyValue("delayMs", paceMs)
                .log("HTTP 429 for a request sent before the pace was last raised; keeping {} ms", paceMs);
        nextAllowedAtNanos = Math.max(nextAllowedAtNanos, nanoTime.getAsLong() + jittered(currentDelayNanos));
    }

    private void increaseDelay(@Nullable String retryAfter) {
        boolean wasHealthy = currentDelayNanos == 0L;
        long calculated = currentDelayNanos == 0L
                ? initialDelayNanos
                : multiplyCapped(currentDelayNanos, config.backoffMultiplier());
        long retryAfterNanos = retryAfterDelayNanos(retryAfter);
        currentDelayNanos = Math.min(maxDelayNanos, Math.max(calculated, retryAfterNanos));
        // INFO on entering degraded mode (rare, actionable); subsequent adjustments at DEBUG.
        long paceMs = Duration.ofNanos(currentDelayNanos).toMillis();
        LOG.atLevel(wasHealthy ? Level.INFO : Level.DEBUG)
                .addKeyValue("delayMs", paceMs)
                .log("Yahoo Finance returned HTTP 429; pacing requests by {} ms", paceMs);
        long scheduledDelayNanos = jittered(currentDelayNanos);
        long candidateNextAllowed = nanoTime.getAsLong() + scheduledDelayNanos;
        nextAllowedAtNanos = Math.max(nextAllowedAtNanos, candidateNextAllowed);
    }

    private void decreaseDelay() {
        if (currentDelayNanos == 0L) {
            return;
        }
        long reduced = (long) Math.floor(currentDelayNanos * config.recoveryFactor());
        currentDelayNanos = reduced <= initialDelayNanos ? 0L : reduced;
        if (currentDelayNanos == 0L) {
            nextAllowedAtNanos = 0L;
            LOG.atInfo().log("Yahoo Finance rate limit recovered; pacing disabled");
        }
    }

    private long retryAfterDelayNanos(@Nullable String retryAfter) {
        Duration retryAfterDelay = parseRetryAfter(retryAfter);
        return retryAfterDelay == null ? 0L : retryAfterDelay.toNanos();
    }

    private @Nullable Duration parseRetryAfter(@Nullable String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return null;
        }
        String value = retryAfter.strip();
        try {
            long seconds = Long.parseLong(value);
            return seconds > 0L ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException ignored) {
            // Retry-After may also be an HTTP-date.
        }
        try {
            Instant retryAt = DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from);
            Duration delay = Duration.between(instantNow.get(), retryAt);
            return delay.isNegative() || delay.isZero() ? null : delay;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private long multiplyCapped(long value, double factor) {
        double multiplied = value * factor;
        return multiplied >= maxDelayNanos ? maxDelayNanos : (long) Math.ceil(multiplied);
    }

    private long jittered(long baseNanos) {
        double jitter = config.jitterFactor();
        if (jitter == 0.0 || baseNanos == 0L) {
            return baseNanos;
        }
        double sample = Math.max(0.0, Math.min(1.0, random.getAsDouble()));
        double min = Math.max(0.0, 1.0 - jitter);
        double max = 1.0 + jitter;
        return Math.max(0L, Math.round(baseNanos * (min + sample * (max - min))));
    }

    private static void sleepThread(Duration delay) throws InterruptedException {
        long millis = delay.toMillis();
        int nanos = (int) (delay.toNanos() - Duration.ofMillis(millis).toNanos());
        Thread.sleep(millis, nanos);
    }
}
