package io.github.dimazigel.yfinance.screener;

/**
 * A field of Yahoo's screener: something a {@link ScreenQuery} can filter on and a
 * {@link ScreenOptions} can sort by. The fields are {@link EquityScreenField} for equities and
 * {@link FundScreenField} for mutual funds.
 */
public sealed interface ScreenField permits EquityScreenField, FundScreenField {

    /** Yahoo's id for the field, e.g. {@code intradaymarketcap}. */
    String key();

    /** Whether the field holds numbers or text; it decides which comparisons apply. */
    Type type();

    /** The kind of value a screener field holds. */
    enum Type {
        /** Compared with numbers: equal, greater, less, between. */
        NUMBER,
        /** Compared for equality with text, e.g. a region code or a sector name. */
        STRING
    }
}
