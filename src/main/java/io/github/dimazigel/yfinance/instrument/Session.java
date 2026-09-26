package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;

/** The regular-session intraday tier; every class except mutual funds (NAV once a day). */
public record Session(BigDecimal open, BigDecimal dayLow, BigDecimal dayHigh, long volume) {}
