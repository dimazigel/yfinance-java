package io.github.dimazigel.yfinance.batch;

/** Why a symbol produced no value although nothing went wrong on the wire. Not worth retrying. */
public enum SkipReason {
    /** Yahoo does not know the symbol (absent from the quote response, or 404 on quoteSummary). */
    UNKNOWN_SYMBOL,
    /** The caller asked for one asset class and the symbol is another. */
    WRONG_ASSET_CLASS,
    /** A guaranteed field was missing after all sources; see {@code Unclassified.missing()}. */
    DOWNGRADED,
    /** The requested data set does not exist for this asset class (e.g. statements for an index). */
    NOT_AVAILABLE_FOR_CLASS,
    /** A quoteSummary module the class guarantees was absent for this symbol. */
    MODULE_ABSENT
}
