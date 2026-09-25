package io.ziggy.yfinance.exception;

import org.jspecify.annotations.Nullable;

/** Raised when Yahoo returns an error envelope or unparseable/empty data. */
public class YFDataException extends YFinanceException {

    public YFDataException(String message) {
        super(message);
    }

    public YFDataException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
