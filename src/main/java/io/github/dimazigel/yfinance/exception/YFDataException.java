package io.github.dimazigel.yfinance.exception;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/** Raised when Yahoo returns an error envelope or unparseable/empty data, or the call failed on the wire. */
public class YFDataException extends YFinanceException {

    public YFDataException(String message) {
        super(message);
    }

    public YFDataException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /**
     * {@code true} only when the direct cause is an {@link IOException} (a transport failure
     * wrapped by the client); a Yahoo error envelope or malformed data is not retryable.
     */
    @Override
    public boolean isRetryable() {
        return getCause() instanceof IOException;
    }
}
