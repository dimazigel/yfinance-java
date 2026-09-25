package io.ziggy.yfinance.exception;

import org.jspecify.annotations.Nullable;

/** Raised when Yahoo's cookie/crumb authentication handshake fails. */
public class YFAuthException extends YFinanceException {

    public YFAuthException(String message) {
        super(message);
    }

    public YFAuthException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
