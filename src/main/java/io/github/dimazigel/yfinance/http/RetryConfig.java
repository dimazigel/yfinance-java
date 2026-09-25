package io.github.dimazigel.yfinance.http;

import java.time.Duration;
import java.util.Objects;

/**
 * Retry policy for transient server errors (HTTP 500/502/503/504). Every Yahoo call is an
 * idempotent GET, so retrying is always safe; the question is only how long to keep trying.
 *
 * @param maxAttempts  total attempts per request (1 = never retry)
 * @param initialDelay wait before the second attempt; doubles on each further attempt
 * @param maxDelay     cap on any single wait, also applied to a {@code Retry-After} the server sends
 */
public record RetryConfig(int maxAttempts, Duration initialDelay, Duration maxDelay) {

    public RetryConfig {
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (initialDelay.isNegative() || initialDelay.isZero()) {
            throw new IllegalArgumentException("initialDelay must be positive");
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= initialDelay");
        }
    }

    /** Three attempts: waits of 500 ms and 1 s, never more than 5 s. */
    public static RetryConfig defaults() {
        return new RetryConfig(3, Duration.ofMillis(500), Duration.ofSeconds(5));
    }

    /** A single attempt; server errors surface immediately. */
    public static RetryConfig disabled() {
        return new RetryConfig(1, Duration.ofMillis(500), Duration.ofSeconds(5));
    }
}
