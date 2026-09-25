package io.github.dimazigel.yfinance.http;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
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

/**
 * Shared adaptive throttle for a Yahoo client. It reacts quickly to HTTP 429 and recovers
 * conservatively after successful responses.
 */
final class AdaptiveRateLimiter {

    private static final Logger LOG = System.getLogger(AdaptiveRateLimiter.class.getName());

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

    void beforeRequest() throws InterruptedException {
        if (!config.enabled()) {
            return;
        }
        while (true) {
            Duration wait;
            synchronized (this) {
                long remainingNanos = nextAllowedAtNanos - nanoTime.getAsLong();
                if (remainingNanos <= 0L) {
                    // While degraded, pace every request by the current delay — not just the first
                    // one after a 429 — so traffic does not burst straight back into the limit.
                    if (currentDelayNanos > 0L) {
                        nextAllowedAtNanos = nanoTime.getAsLong() + jittered(currentDelayNanos);
                    }
                    return;
                }
                wait = Duration.ofNanos(remainingNanos);
            }
            LOG.log(Level.DEBUG, "Rate limited; waiting {0} ms before next request", wait.toMillis());
            sleeper.sleep(wait);
        }
    }

    /** Total attempts the interceptor may make per request (1 when throttling is disabled). */
    int maxAttemptsPerRequest() {
        return config.enabled() ? config.maxAttempts() : 1;
    }

    synchronized void onResponse(int code, @Nullable String retryAfter) {
        if (!config.enabled()) {
            return;
        }
        if (code == 429) {
            increaseDelay(retryAfter);
        } else if (code >= 200 && code < 400) {
            decreaseDelay();
        }
    }

    synchronized Duration currentDelay() {
        return Duration.ofNanos(currentDelayNanos);
    }

    private void increaseDelay(@Nullable String retryAfter) {
        boolean wasHealthy = currentDelayNanos == 0L;
        long calculated = currentDelayNanos == 0L
                ? initialDelayNanos
                : multiplyCapped(currentDelayNanos, config.backoffMultiplier());
        long retryAfterNanos = retryAfterDelayNanos(retryAfter);
        currentDelayNanos = Math.min(maxDelayNanos, Math.max(calculated, retryAfterNanos));
        // INFO on entering degraded mode (rare, actionable); subsequent adjustments at DEBUG.
        LOG.log(wasHealthy ? Level.INFO : Level.DEBUG,
                "Yahoo Finance returned HTTP 429; pacing requests by {0} ms",
                Duration.ofNanos(currentDelayNanos).toMillis());
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
            LOG.log(Level.INFO, "Yahoo Finance rate limit recovered; pacing disabled");
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
