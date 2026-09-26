package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.list;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Mutual fund detail fields. */
public final class MutualFundDetailSpecs {

    private MutualFundDetailSpecs() {}

    private static final List<FieldSpec> OWN = List.of(
            required("longBusinessSummary", RAW, "qs:assetProfile.longBusinessSummary"),
            required("morningstar.overallRating", RAW, "qs:defaultKeyStatistics.morningStarOverallRating"),
            required("morningstar.riskRating", RAW, "qs:defaultKeyStatistics.morningStarRiskRating"),
            required("annualHoldingsTurnover", RAW, "qs:defaultKeyStatistics.annualHoldingsTurnover"),
            required("lastCapGain", RAW, "qs:defaultKeyStatistics.lastCapGain"),
            required("lastDividendValue", RAW, "qs:defaultKeyStatistics.lastDividendValue"),
            required("beta3Year", RAW, "qs:defaultKeyStatistics.beta3Year"),
            required("minimums.initial", RAW, "qs:fundProfile.initInvestment"),
            required("minimums.subsequent", RAW, "qs:fundProfile.subseqInvestment"),
            list("brokerages", "qs:fundProfile.brokerages"),
            required("loadAdjustedReturns.oneYear", RAW, "qs:fundPerformance.loadAdjustedReturns.oneYear"),
            required("loadAdjustedReturns.threeYear", RAW, "qs:fundPerformance.loadAdjustedReturns.threeYear"),
            required("loadAdjustedReturns.fiveYear", RAW, "qs:fundPerformance.loadAdjustedReturns.fiveYear"),
            required("loadAdjustedReturns.tenYear", RAW, "qs:fundPerformance.loadAdjustedReturns.tenYear"),
            required("rankInCategory.ytd", RAW, "qs:fundPerformance.rankInCategory.ytd"),
            required("rankInCategory.oneMonth", RAW, "qs:fundPerformance.rankInCategory.oneMonth"),
            required("rankInCategory.threeMonth", RAW, "qs:fundPerformance.rankInCategory.threeMonth"),
            required("rankInCategory.oneYear", RAW, "qs:fundPerformance.rankInCategory.oneYear"),
            required("rankInCategory.threeYear", RAW, "qs:fundPerformance.rankInCategory.threeYear"),
            required("rankInCategory.fiveYear", RAW, "qs:fundPerformance.rankInCategory.fiveYear"),
            required("styleBoxUrl", RAW, "qs:fundProfile.styleBoxUrl"));

    public static final List<FieldSpec> DETAIL = Specs.concat(FundDetailSpecs.COMMON, OWN);
}
