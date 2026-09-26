package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;

/** Dividends paid over the trailing twelve months; yield is a fraction. */
public record TrailingDividend(BigDecimal rate, BigDecimal yield) {}
