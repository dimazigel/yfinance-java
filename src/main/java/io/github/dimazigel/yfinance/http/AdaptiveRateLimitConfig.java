package io.github.dimazigel.yfinance.http;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for adaptive client-side throttling after Yahoo returns HTTP 429.
 *
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

    public static AdaptiveRateLimitConfig defaults() {
        return new AdaptiveRateLimitConfig(
                true,
                Duration.ofMillis(500),
                Duration.ofSeconds(30),
                2.0,
                0.5,
                0.2,
                3);
    }

    public static AdaptiveRateLimitConfig disabled() {
        return new AdaptiveRateLimitConfig(
                false,
                Duration.ofMillis(500),
                Duration.ofSeconds(30),
                2.0,
                0.5,
                0.0,
                1);
    }
}
