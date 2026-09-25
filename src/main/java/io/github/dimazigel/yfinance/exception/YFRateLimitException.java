package io.github.dimazigel.yfinance.exception;

import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Raised when Yahoo responds with HTTP 429 (Too Many Requests). */
public class YFRateLimitException extends YFinanceException {

    private final transient @Nullable Duration retryAfter;

    public YFRateLimitException(String message) {
        this(message, null);
    }

    public YFRateLimitException(String message, @Nullable Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    /** The {@code Retry-After} delay Yahoo asked us to wait, if it was provided. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
