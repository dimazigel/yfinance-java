package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;

/** ETFs and mutual funds: what both guarantee at snapshot depth. Detail-tier fund data is in {@code detail.FundDetail}. */
public sealed interface Fund permits Etf, MutualFund {
    BigDecimal ytdReturn();

    BigDecimal threeMonthReturn();
}
