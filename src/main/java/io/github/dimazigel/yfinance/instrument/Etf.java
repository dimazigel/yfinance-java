package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * An exchange-traded fund (Yahoo {@code quoteType} ETF). {@code expenseRatio} is present for the
 * large majority of surveyed ETFs (89 %); UCITS listings mostly lack it, hence {@code Optional}
 * rather than the guarantee {@link MutualFund} gives the same field.
 */
public record Etf(
        Core core,
        Session session,
        Optional<TopOfBook> book,
        BigDecimal ytdReturn,
        BigDecimal threeMonthReturn,
        Optional<BigDecimal> netAssets,
        Optional<BigDecimal> expenseRatio,
        Optional<BigDecimal> yield,
        Optional<BigDecimal> navPrice,
        Optional<BigDecimal> beta3Year,
        Optional<BigDecimal> trailingThreeMonthNavReturns,
        Optional<BigDecimal> trailingPE,
        Optional<EquityLikeStats> equityLikeStats,
        Optional<TrailingDividend> trailingDividend,
        Optional<PostMarket> postMarket,
        Instant fetchedAt) implements Instrument, IntradayTraded, Quoted, Fund {

    @Override
    public AssetClass assetClass() {
        return AssetClass.ETF;
    }

    /** Equity-style valuation facts Yahoo carries for some ETFs and not others; absent means none of it was quoted. */
    public record EquityLikeStats(BigDecimal bookValue, BigDecimal priceToBook, long sharesOutstanding, QuoteCurrency financialCurrency) {}
}
