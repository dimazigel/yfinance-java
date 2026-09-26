package io.github.dimazigel.yfinance.assembly;

/** Where a {@link WirePath} reads from: the v7 quote row, or a quoteSummary module. */
public enum Source {
    /** {@code /v7/finance/quote} row for the symbol. */
    V7,
    /** A quoteSummary module (e.g. {@code price}, {@code summaryDetail}). */
    QUOTE_SUMMARY
}
