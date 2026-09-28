package io.github.dimazigel.yfinance.exception;

import org.jspecify.annotations.Nullable;

/**
 * Base type for all yfinance errors. {@link #isRetryable()} says whether the same call may
 * succeed if simply repeated later — the machine-readable form of the README's error table.
 */
public class YFinanceException extends RuntimeException {

    public YFinanceException(String message) {
        super(message);
    }

    public YFinanceException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /**
     * Whether repeating the same call later may succeed: {@code true} for a rate limit, a 5xx
     * or an I/O failure; {@code false} for everything that describes the data or the request
     * (unknown symbol, wrong class, malformed response, authentication). {@code false} here;
     * subtypes override.
     */
    public boolean isRetryable() {
        return false;
    }
}
