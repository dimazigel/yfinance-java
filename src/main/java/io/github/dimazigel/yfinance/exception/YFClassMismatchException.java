package io.github.dimazigel.yfinance.exception;

import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.valueobject.Symbol;

/**
 * Raised by {@code Ticker.as(Class)} when the instrument Yahoo describes is not of the requested
 * class, e.g. {@code "AAPL is EQUITY, not Etf"}. A downgraded instrument reports
 * {@link AssetClass#UNCLASSIFIED}. A {@link YFDataException}, so existing handlers keep working.
 */
public class YFClassMismatchException extends YFDataException {

    private final AssetClass actual;
    private final Class<?> requested;

    public YFClassMismatchException(Symbol symbol, AssetClass actual, Class<?> requested) {
        super(symbol + " is " + actual + ", not " + requested.getSimpleName());
        this.actual = actual;
        this.requested = requested;
    }

    /** The asset class Yahoo actually reports for the symbol. */
    public AssetClass actual() {
        return actual;
    }

    /** The instrument type the caller asked for. */
    public Class<?> requested() {
        return requested;
    }
}
