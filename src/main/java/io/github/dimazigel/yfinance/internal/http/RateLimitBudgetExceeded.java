package io.github.dimazigel.yfinance.internal.http;

import java.io.InterruptedIOException;
import java.time.Duration;

/**
 * Thrown by {@link AdaptiveRateLimitInterceptor} when the limiter's pending wait cannot fit in what
 * is left of the call's timeout: a rate-limit condition, not a transport failure, so
 * {@link YahooFeignClient} surfaces it as a
 * {@link io.github.dimazigel.yfinance.exception.YFRateLimitException} carrying the wait as
 * {@code retryAfter}. An {@link InterruptedIOException} so OkHttp treats it like its own timeout.
 */
final class RateLimitBudgetExceeded extends InterruptedIOException {

    private static final long serialVersionUID = 1L;

    private final transient Duration pendingWait;

    RateLimitBudgetExceeded(Duration pendingWait) {
        super("rate-limit wait of " + pendingWait.toMillis() + " ms exceeds the remaining call timeout");
        this.pendingWait = pendingWait;
    }

    /** How long the limiter wanted the call to wait before sending. */
    Duration pendingWait() {
        return pendingWait;
    }
}
