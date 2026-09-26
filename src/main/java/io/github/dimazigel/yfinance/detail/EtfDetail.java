package io.github.dimazigel.yfinance.detail;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** ETF-specific fund detail. */
public record EtfDetail(
        Symbol symbol,
        String family,
        String legalType,
        LocalDate inceptionDate,
        FundDetail.TrailingReturns trailingReturns,
        List<FundDetail.YearReturn> annualTotalReturns,
        FundDetail.Allocation allocation,
        FundDetail.EquityValuation equityValuation,
        List<FundDetail.Holding> holdings,
        List<FundDetail.SectorWeight> sectorWeightings,
        List<FundDetail.BondRating> bondRatings,
        Optional<String> category,
        Optional<BigDecimal> beta3Year,
        Optional<String> longBusinessSummary,
        Optional<URI> styleBoxUrl,
        Instant fetchedAt) implements FundDetail {

    public EtfDetail {
        holdings = List.copyOf(holdings);
        sectorWeightings = List.copyOf(sectorWeightings);
        bondRatings = List.copyOf(bondRatings);
        annualTotalReturns = List.copyOf(annualTotalReturns);
    }
}
