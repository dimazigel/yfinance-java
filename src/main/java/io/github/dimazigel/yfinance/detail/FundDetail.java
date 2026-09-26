package io.github.dimazigel.yfinance.detail;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared fund detail across ETFs and mutual funds. */
public sealed interface FundDetail permits EtfDetail, MutualFundDetail {

    Symbol symbol();

    String family();

    LocalDate inceptionDate();

    TrailingReturns trailingReturns();

    List<YearReturn> annualTotalReturns();

    Allocation allocation();

    EquityValuation equityValuation();

    List<Holding> holdings();

    List<SectorWeight> sectorWeightings();

    List<BondRating> bondRatings();

    Optional<String> category();

    Instant fetchedAt();

    /** Returns over various periods. */
    record TrailingReturns(BigDecimal ytd, BigDecimal oneMonth, BigDecimal threeMonth, BigDecimal oneYear,
                           BigDecimal threeYear, BigDecimal fiveYear, BigDecimal tenYear, LocalDate asOf) {}

    /** Annual return for one year. */
    record YearReturn(int year, BigDecimal value) {}

    /** Allocation percentages by asset class. */
    record Allocation(BigDecimal stock, BigDecimal bond, BigDecimal cash, BigDecimal preferred,
                      BigDecimal convertible, BigDecimal other) {}

    /** Equity valuation metrics. */
    record EquityValuation(BigDecimal priceToEarnings, BigDecimal priceToBook, BigDecimal priceToSales,
                           BigDecimal priceToCashflow) {}

    /** One holding in the fund. */
    record Holding(String symbol, String name, BigDecimal weight) {}

    /** Sector weight. */
    record SectorWeight(String sector, BigDecimal weight) {}

    /** Bond rating weight. */
    record BondRating(String rating, BigDecimal weight) {}
}
