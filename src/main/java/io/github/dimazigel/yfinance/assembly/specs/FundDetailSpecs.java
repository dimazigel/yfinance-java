package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.list;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.optional;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.EPOCH_DATE;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Fund detail fields shared between ETF and mutual fund. */
public final class FundDetailSpecs {

    private FundDetailSpecs() {}

    public static final List<FieldSpec> COMMON = List.of(
            required("family", RAW, "qs:fundProfile.family", "qs:defaultKeyStatistics.fundFamily"),
            required("inceptionDate", EPOCH_DATE, "qs:defaultKeyStatistics.fundInceptionDate"),
            required("trailingReturns.ytd", RAW, "qs:fundPerformance.trailingReturns.ytd"),
            required("trailingReturns.oneMonth", RAW, "qs:fundPerformance.trailingReturns.oneMonth"),
            required("trailingReturns.threeMonth", RAW, "qs:fundPerformance.trailingReturns.threeMonth"),
            required("trailingReturns.oneYear", RAW, "qs:fundPerformance.trailingReturns.oneYear"),
            required("trailingReturns.threeYear", RAW, "qs:fundPerformance.trailingReturns.threeYear"),
            required("trailingReturns.fiveYear", RAW, "qs:fundPerformance.trailingReturns.fiveYear"),
            required("trailingReturns.tenYear", RAW, "qs:fundPerformance.trailingReturns.tenYear"),
            required("trailingReturns.asOf", EPOCH_DATE, "qs:fundPerformance.trailingReturns.asOfDate"),
            list("annualTotalReturns", "qs:fundPerformance.annualTotalReturns.returns"),
            required("allocation.stock", RAW, "qs:topHoldings.stockPosition"),
            required("allocation.bond", RAW, "qs:topHoldings.bondPosition"),
            required("allocation.cash", RAW, "qs:topHoldings.cashPosition"),
            required("allocation.preferred", RAW, "qs:topHoldings.preferredPosition"),
            required("allocation.convertible", RAW, "qs:topHoldings.convertiblePosition"),
            required("allocation.other", RAW, "qs:topHoldings.otherPosition"),
            required("equityValuation.priceToEarnings", RAW, "qs:topHoldings.equityHoldings.priceToEarnings"),
            required("equityValuation.priceToBook", RAW, "qs:topHoldings.equityHoldings.priceToBook"),
            required("equityValuation.priceToSales", RAW, "qs:topHoldings.equityHoldings.priceToSales"),
            required("equityValuation.priceToCashflow", RAW, "qs:topHoldings.equityHoldings.priceToCashflow"),
            list("holdings", "qs:topHoldings.holdings"),
            list("sectorWeightings", "qs:topHoldings.sectorWeightings"),
            list("bondRatings", "qs:topHoldings.bondRatings"),
            optional("category", RAW, "qs:fundProfile.categoryName", "qs:defaultKeyStatistics.category", "qs:fundPerformance.fundCategoryName"));
}
