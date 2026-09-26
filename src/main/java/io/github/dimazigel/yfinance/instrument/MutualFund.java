package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * A mutual fund (Yahoo {@code quoteType} MUTUALFUND). Priced once a day by NAV: no intraday
 * session and no bid/ask, so unlike {@link Etf} this class implements neither {@link IntradayTraded}
 * nor {@link Quoted}. Unlike {@link Etf}, where the survey found {@code expenseRatio} on only 89 %
 * of listings (UCITS funds mostly lack it), the survey found it on 100 % of surveyed mutual funds —
 * likewise {@code netAssets}, {@code yield}, {@code dividendRate}, {@code ytdReturn} and
 * {@code threeMonthReturn} — so all six are guaranteed here (the Intrinsic rule, design D5): a
 * symbol lacking any of them is not treated as a mutual fund.
 */
public record MutualFund(
        Core core,
        BigDecimal netAssets,
        BigDecimal expenseRatio,
        BigDecimal yield,
        BigDecimal dividendRate,
        BigDecimal ytdReturn,
        BigDecimal threeMonthReturn,
        Optional<Etf.EquityLikeStats> equityLikeStats,
        Optional<BigDecimal> trailingPE,
        Optional<TrailingDividend> trailingDividend,
        Instant fetchedAt) implements Instrument, Fund {

    @Override
    public AssetClass assetClass() {
        return AssetClass.MUTUAL_FUND;
    }
}
