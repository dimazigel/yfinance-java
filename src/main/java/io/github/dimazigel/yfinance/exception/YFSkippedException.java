package io.github.dimazigel.yfinance.exception;

import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.valueobject.Symbol;

/**
 * What {@link io.github.dimazigel.yfinance.batch.Outcome.Skipped#orElseThrow()} throws: a
 * {@link YFMissingDataException} that also carries the {@link SkipReason}, so a generic fan-out
 * ({@code Tickers.fetch}) can turn a single-{@code Ticker} skip back into an
 * {@link io.github.dimazigel.yfinance.batch.Outcome.Skipped} instead of a retryable failure.
 * {@link #field()} is the skip detail and {@link #subject()} the symbol's value, as for the parent.
 */
public class YFSkippedException extends YFMissingDataException {

    private final Symbol symbol;
    private final SkipReason reason;

    public YFSkippedException(Symbol symbol, SkipReason reason, String detail) {
        super(detail, symbol.value(), symbol + " skipped: " + reason + " (" + detail + ")");
        this.symbol = symbol;
        this.reason = reason;
    }

    /** The symbol the library chose not to answer for. */
    public Symbol symbol() {
        return symbol;
    }

    /** Why there is nothing to return; never a transport failure, so not worth retrying. */
    public SkipReason reason() {
        return reason;
    }
}
