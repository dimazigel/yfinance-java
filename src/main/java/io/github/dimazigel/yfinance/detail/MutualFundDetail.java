package io.github.dimazigel.yfinance.detail;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Mutual fund-specific fund detail. */
public record MutualFundDetail(
        Symbol symbol,
        String family,
        LocalDate inceptionDate,
        FundDetail.TrailingReturns trailingReturns,
        List<FundDetail.YearReturn> annualTotalReturns,
        FundDetail.Allocation allocation,
        FundDetail.EquityValuation equityValuation,
        List<FundDetail.Holding> holdings,
        List<FundDetail.SectorWeight> sectorWeightings,
        List<FundDetail.BondRating> bondRatings,
        Optional<String> category,
        String longBusinessSummary,
        Morningstar morningstar,
        BigDecimal annualHoldingsTurnover,
        BigDecimal lastCapGain,
        BigDecimal lastDividendValue,
        BigDecimal beta3Year,
        Minimums minimums,
        List<String> brokerages,
        LoadAdjustedReturns loadAdjustedReturns,
        RankInCategory rankInCategory,
        URI styleBoxUrl,
        Instant fetchedAt) implements FundDetail {

    public MutualFundDetail {
        holdings = List.copyOf(holdings);
        sectorWeightings = List.copyOf(sectorWeightings);
        bondRatings = List.copyOf(bondRatings);
        annualTotalReturns = List.copyOf(annualTotalReturns);
        brokerages = List.copyOf(brokerages);
    }

    /** Morningstar ratings. */
    public record Morningstar(int overallRating, int riskRating) {}

    /** Minimum investment amounts. */
    public record Minimums(BigDecimal initial, BigDecimal subsequent) {}

    /** Load-adjusted returns over various periods. */
    public record LoadAdjustedReturns(BigDecimal oneYear, BigDecimal threeYear, BigDecimal fiveYear,
                                      BigDecimal tenYear) {}

    /** Rank in category over various periods. */
    public record RankInCategory(BigDecimal ytd, BigDecimal oneMonth, BigDecimal threeMonth, BigDecimal oneYear,
                                 BigDecimal threeYear, BigDecimal fiveYear) {}
}
