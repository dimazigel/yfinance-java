package io.github.dimazigel.yfinance.assembly.specs;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import java.util.List;

/** Specifications and module lists for the detail tier. */
public final class DetailSpecs {

    private DetailSpecs() {}

    /**
     * Returns the module list for detail specs of the given {@code assetClass}.
     * ETF/MUTUAL_FUND, EQUITY and CRYPTO have detail; others have empty.
     */
    public static List<String> modules(AssetClass assetClass) {
        return switch (assetClass) {
            case ETF, MUTUAL_FUND -> List.of("price", "quoteType", "summaryDetail", "defaultKeyStatistics",
                    "assetProfile", "fundProfile", "topHoldings", "fundPerformance");
            case EQUITY -> List.of("price", "quoteType", "summaryDetail", "defaultKeyStatistics",
                    "financialData", "assetProfile", "summaryProfile", "calendarEvents", "secFilings", "recommendationTrend",
                    "upgradeDowngradeHistory", "earningsTrend", "earningsHistory", "majorHoldersBreakdown",
                    "institutionOwnership", "fundOwnership", "insiderHolders", "insiderTransactions",
                    "netSharePurchaseActivity");
            case CRYPTO -> List.of("price", "quoteType", "summaryDetail", "assetProfile");
            case INDEX, FX, FUTURE, UNCLASSIFIED -> List.of();
        };
    }

    /**
     * Returns the FieldSpec list for detail of the given {@code assetClass}.
     */
    public static List<FieldSpec> forClass(AssetClass assetClass) {
        return switch (assetClass) {
            case ETF -> EtfDetailSpecs.DETAIL;
            case MUTUAL_FUND -> MutualFundDetailSpecs.DETAIL;
            case EQUITY -> EquityDetailSpecs.DETAIL;
            case CRYPTO -> CryptoDetailSpecs.DETAIL;
            case INDEX, FX, FUTURE, UNCLASSIFIED -> List.of();
        };
    }
}
