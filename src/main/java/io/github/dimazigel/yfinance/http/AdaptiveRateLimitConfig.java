package io.github.dimazigel.yfinance.http;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for adaptive client-side throttling after Yahoo returns HTTP 429. Start from
 * {@link #defaults()} or {@link #disabled()} and derive variants with the {@code with...} methods;
 * validation lives in the canonical constructor, so every wither validates too.
 *
 * @param enabled whether 429 responses adapt the pace and are retried at all
 * @param initialDelay the pace after the first 429, 500 ms by default; positive
 * @param maxDelay cap on the pace (and on a {@code Retry-After} Yahoo sends), 10 s by default;
 *     {@link EndpointConfig#callTimeout()} bounds the whole call including the paced waits, so a
 *     cap longer than the call timeout is clamped to it when the client is built
 * @param backoffMultiplier growth of the pace per consecutive 429, 2.0 by default; greater than 1
 * @param recoveryFactor shrink of the pace per success while degraded, 0.5 by default; strictly
 *     between 0 and 1
 * @param jitterFactor random spread applied to each scheduled wait, 0.2 (±20 %) by default;
 *     between 0 and 1
 * @param maxAttempts total attempts per request (1 = never retry a 429; N > 1 = wait the adapted
 *     delay and retry up to N-1 times before surfacing the 429)
 */
public record AdaptiveRateLimitConfig(
        boolean enabled,
        Duration initialDelay,
        Duration maxDelay,
        double backoffMultiplier,
        double recoveryFactor,
        double jitterFactor,
        int maxAttempts) {

    public AdaptiveRateLimitConfig {
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (initialDelay.isNegative() || initialDelay.isZero()) {
            throw new IllegalArgumentException("initialDelay must be positive");
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= initialDelay");
        }
        if (backoffMultiplier <= 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be > 1.0");
        }
        if (recoveryFactor <= 0.0 || recoveryFactor >= 1.0) {
            throw new IllegalArgumentException("recoveryFactor must be > 0.0 and < 1.0");
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
    }

    /** What {@link EndpointConfig#production()} uses: on, 500 ms → 10 s, ×2, ½, ±20 %, 3 attempts. */
    public static AdaptiveRateLimitConfig defaults() {
        return new AdaptiveRateLimitConfig(
                true,
                Duration.ofMillis(500),
                Duration.ofSeconds(10),
                2.0,
                0.5,
                0.2,
                3);
    }

    /** Returns a copy with throttling and 429-retries switched on or off. */
    public AdaptiveRateLimitConfig withEnabled(boolean enabled) {
        return new AdaptiveRateLimitConfig(enabled, initialDelay, maxDelay, backoffMultiplier, recoveryFactor, jitterFactor, maxAttempts);
    }

    /** Returns a copy with a different first pace after a 429; positive, at most {@link #maxDelay()}. */
    public AdaptiveRateLimitConfig withInitialDelay(Duration initialDelay) {
        return new AdaptiveRateLimitConfig(enabled, initialDelay, maxDelay, backoffMultiplier, recoveryFactor, jitterFactor, maxAttempts);
    }

    /** Returns a copy with a different pace cap; at least {@link #initialDelay()}. */
    public AdaptiveRateLimitConfig withMaxDelay(Duration maxDelay) {
        return new AdaptiveRateLimitConfig(enabled, initialDelay, maxDelay, backoffMultiplier, recoveryFactor, jitterFactor, maxAttempts);
    }

    /** Returns a copy with a different growth factor per consecutive 429; greater than 1. */
    public AdaptiveRateLimitConfig withBackoffMultiplier(double backoffMultiplier) {
        return new AdaptiveRateLimitConfig(enabled, initialDelay, maxDelay, backoffMultiplier, recoveryFactor, jitterFactor, maxAttempts);
    }

    /** Returns a copy with a different shrink factor per success while degraded; strictly between 0 and 1. */
    public AdaptiveRateLimitConfig withRecoveryFactor(double recoveryFactor) {
        return new AdaptiveRateLimitConfig(enabled, initialDelay, maxDelay, backoffMultiplier, recoveryFactor, jitterFactor, maxAttempts);
    }

    /** Returns a copy with a different jitter on scheduled waits, as a fraction between 0 and 1. */
    public AdaptiveRateLimitConfig withJitterFactor(double jitterFactor) {
        return new AdaptiveRateLimitConfig(enabled, initialDelay, maxDelay, backoffMultiplier, recoveryFactor, jitterFactor, maxAttempts);
    }

    /** Returns a copy with a different total number of attempts per request; at least 1 (1 = never retry a 429). */
    public AdaptiveRateLimitConfig withMaxAttempts(int maxAttempts) {
        return new AdaptiveRateLimitConfig(enabled, initialDelay, maxDelay, backoffMultiplier, recoveryFactor, jitterFactor, maxAttempts);
    }

    /** Throttling and 429-retries off: a 429 surfaces at once as a {@code YFRateLimitException}. */
    public static AdaptiveRateLimitConfig disabled() {
        return new AdaptiveRateLimitConfig(
                false,
                Duration.ofMillis(500),
                Duration.ofSeconds(10),
                2.0,
                0.5,
                0.0,
                1);
    }
}
