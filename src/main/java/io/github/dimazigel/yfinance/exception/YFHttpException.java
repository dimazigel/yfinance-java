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

    public int status() {
        return status;
    }

    public String path() {
        return path;
    }
}
