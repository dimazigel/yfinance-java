package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;

/**
 * Equity-style valuation facts Yahoo carries for some funds and not others (an {@link Etf} or
 * {@link MutualFund}); the cluster is absent when none of it was quoted.
 */
public record EquityLikeStats(BigDecimal bookValue, BigDecimal priceToBook, long sharesOutstanding, QuoteCurrency financialCurrency) {}
