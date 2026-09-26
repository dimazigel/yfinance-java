package io.github.dimazigel.yfinance.exception;

/**
 * A value the caller asked for that Yahoo has nothing for. Its subtype {@link YFSkippedException}
 * is what {@link io.github.dimazigel.yfinance.batch.Outcome.Skipped#orElseThrow()} throws — and
 * therefore what {@code Ticker.instrument()} and {@code Ticker.detail(...)} throw for a symbol
 * Yahoo does not know or a module it omits. A {@link YFDataException}, so existing handlers keep working.
 */
public class YFMissingDataException extends YFDataException {

    private final String field;
    private final String subject;

    public YFMissingDataException(String field, String subject, String message) {
        super(message);
        this.field = field;
        this.subject = subject;
    }

    /**
     * The name the caller gave the missing value, e.g. {@code marketCap}. When thrown from an
     * {@link io.github.dimazigel.yfinance.batch.Outcome.Skipped} this is the skip's {@code detail}:
     * a comma-separated field list ({@code "financials.totalRevenue,analysts.recommendationKey"}),
     * a reported asset class, or a sentence such as {@code "core incomplete: [firstTradeDate]"}.
     */
    public String field() {
        return field;
    }

    /** What the value was requested for, e.g. a symbol such as {@code ^GSPC}. */
    public String subject() {
        return subject;
    }
}
