package io.github.dimazigel.yfinance.exception;

/** Yahoo answered with a non-success HTTP status; {@link #status()} lets callers distinguish 404 from 5xx. */
public class YFHttpException extends YFDataException {

    private final int status;
    private final String path;

    public YFHttpException(int status, String path, String message) {
        super(message);
        this.status = status;
        this.path = path;
    }

    /** The HTTP status Yahoo answered with. */
    public int status() {
        return status;
    }

    /** {@code true} for a server error (status 500 and above), {@code false} for a client error such as 404. */
    @Override
    public boolean isRetryable() {
        return status >= 500;
    }

    /** The request path, e.g. {@code /v8/finance/chart/AAPL}. */
    public String path() {
        return path;
    }
}
