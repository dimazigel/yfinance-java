package io.ziggy.yfinance.exception;

import org.jspecify.annotations.Nullable;

/** Base type for all yfinance errors. */
public class YFinanceException extends RuntimeException {

    public YFinanceException(String message) {
        super(message);
    }

    public YFinanceException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
