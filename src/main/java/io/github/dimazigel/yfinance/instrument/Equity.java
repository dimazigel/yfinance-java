package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** A common stock (Yahoo {@code quoteType} EQUITY). Non-null fields are the Appendix A "R" rows. */
public record Equity(
        Core core,
        Session session,
        Optional<TopOfBook> book,
        Valuation valuation,
        NextEarnings nextEarnings,
        Optional<BigDecimal> bookValue,
        Optional<BigDecimal> priceToBook,
        Optional<BigDecimal> trailingEps,
        Optional<BigDecimal> forwardEps,
        Optional<BigDecimal> forwardPE,
        Optional<BigDecimal> trailingPE,
        Optional<TrailingDividend> trailingDividend,
        Optional<CurrentDividend> currentDividend,
        Optional<CurrentYearEps> currentYearEps,
        Optional<String> averageAnalystRating,
        Optional<PostMarket> postMarket,
        Instant fetchedAt) implements Instrument, IntradayTraded, Quoted {

    @Override
    public AssetClass assetClass() {
        return AssetClass.EQUITY;
    }

    /** Guaranteed valuation facts; a symbol lacking any of them is not treated as an equity. */
    public record Valuation(
            BigDecimal marketCap,
            long sharesOutstanding,
            long impliedSharesOutstanding,
            QuoteCurrency financialCurrency) {}

    /** Next earnings release window; {@code isEstimate} when Yahoo has not confirmed the date. */
    public record NextEarnings(Instant expected, Instant windowStart, Instant windowEnd, boolean isEstimate) {}

    /** Forward (declared) dividend; yield is a fraction. Absent for non-payers. */
    public record CurrentDividend(BigDecimal rate, BigDecimal yield) {}

    /** Consensus EPS for the current fiscal year and the price relative to it. */
    public record CurrentYearEps(BigDecimal eps, BigDecimal priceToEps) {}
}
