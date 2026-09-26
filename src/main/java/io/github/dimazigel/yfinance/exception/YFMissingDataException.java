package io.github.dimazigel.yfinance.exception;

/**
 * Raised by {@code require(...)} helpers when a value the caller insists on is one Yahoo did not
 * report for this instrument (for example {@code marketCap} on an index). A {@link YFDataException},
 * so existing handlers keep working; {@link #field()} and {@link #subject()} say what was missing
 * and for what.
 */
public class YFMissingDataException extends YFDataException {

    private final String field;
    private final String subject;

    public YFMissingDataException(String field, String subject, String message) {
        super(message);
        this.field = field;
        this.subject = subject;
    }

    /** The name the caller gave the missing value, e.g. {@code marketCap}. */
    public String field() {
        return field;
    }

    /** What the value was requested for, e.g. a symbol such as {@code ^GSPC}. */
    public String subject() {
        return subject;
    }
}
