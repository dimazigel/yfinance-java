package io.github.dimazigel.yfinance.exception;

/**
 * What {@link io.github.dimazigel.yfinance.batch.Outcome.Skipped#orElseThrow()} throws — and
 * therefore what {@code Ticker.instrument()} and {@code Ticker.detail(...)} throw for a symbol
 * Yahoo does not know or a module it omits. {@link #field()} is the skip detail (the missing-field
 * list, or e.g. {@code "quoteSummary 404"}) and {@link #subject()} the symbol. A
 * {@link YFDataException}, so existing handlers keep working.
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
