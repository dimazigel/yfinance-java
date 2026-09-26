package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.clustered;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.list;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.optional;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.EPOCH_DATE;
import static io.github.dimazigel.yfinance.assembly.Unit.PERCENT;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Equity detail: every row of the appendix "Equity — detail" table. */
public final class EquityDetailSpecs {

    private EquityDetailSpecs() {}

    private static final List<FieldSpec> PROFILE = List.of(
            required("profile.sector", RAW, "qs:assetProfile.sector", "qs:summaryProfile.sector"),
            required("profile.industry", RAW, "qs:assetProfile.industry", "qs:summaryProfile.industry"),
            required("profile.country", RAW, "qs:assetProfile.country", "qs:summaryProfile.country"),
            required("profile.city", RAW, "qs:assetProfile.city"),
            required("profile.address1", RAW, "qs:assetProfile.address1"),
            required("profile.zip", RAW, "qs:assetProfile.zip"),
            required("profile.website", RAW, "qs:assetProfile.website", "qs:summaryProfile.website"),
            required("profile.longBusinessSummary", RAW, "qs:assetProfile.longBusinessSummary", "qs:summaryProfile.longBusinessSummary"),
            list("profile.officers", "qs:assetProfile.companyOfficers"),
            optional("profile.fullTimeEmployees", RAW, "qs:assetProfile.fullTimeEmployees"),
            optional("profile.phone", RAW, "qs:assetProfile.phone"),
            optional("profile.state", RAW, "qs:assetProfile.state"),
            optional("profile.irWebsite", RAW, "qs:assetProfile.irWebsite"),
            clustered("governance", "profile.governance.auditRisk", RAW, "qs:assetProfile.auditRisk"),
            clustered("governance", "profile.governance.boardRisk", RAW, "qs:assetProfile.boardRisk"),
            clustered("governance", "profile.governance.compensationRisk", RAW, "qs:assetProfile.compensationRisk"),
            clustered("governance", "profile.governance.shareholderRightsRisk", RAW, "qs:assetProfile.shareHolderRightsRisk"),
            clustered("governance", "profile.governance.overallRisk", RAW, "qs:assetProfile.overallRisk"));

    private static final List<FieldSpec> STATISTICS = List.of(
            required("statistics.floatShares", RAW, "qs:defaultKeyStatistics.floatShares"),
            required("statistics.heldPercentInsiders", RAW, "qs:defaultKeyStatistics.heldPercentInsiders"),
            required("statistics.heldPercentInstitutions", RAW, "qs:defaultKeyStatistics.heldPercentInstitutions"),
            required("statistics.profitMargins", RAW, "qs:defaultKeyStatistics.profitMargins", "qs:financialData.profitMargins"),
            optional("statistics.beta", RAW, "qs:summaryDetail.beta", "qs:defaultKeyStatistics.beta"),
            optional("statistics.enterpriseValue", RAW, "qs:defaultKeyStatistics.enterpriseValue"),
            optional("statistics.enterpriseToRevenue", RAW, "qs:defaultKeyStatistics.enterpriseToRevenue"),
            optional("statistics.enterpriseToEbitda", RAW, "qs:defaultKeyStatistics.enterpriseToEbitda"),
            clustered("fiscal", "statistics.fiscal.lastFiscalYearEnd", EPOCH_DATE, "qs:defaultKeyStatistics.lastFiscalYearEnd"),
            clustered("fiscal", "statistics.fiscal.nextFiscalYearEnd", EPOCH_DATE, "qs:defaultKeyStatistics.nextFiscalYearEnd"),
            clustered("fiscal", "statistics.fiscal.mostRecentQuarter", EPOCH_DATE, "qs:defaultKeyStatistics.mostRecentQuarter"),
            optional("statistics.pegRatio", RAW, "qs:defaultKeyStatistics.pegRatio"),
            optional("statistics.payoutRatio", RAW, "qs:summaryDetail.payoutRatio"),
            optional("statistics.priceToSales", RAW, "qs:summaryDetail.priceToSalesTrailing12Months"),
            optional("statistics.earningsQuarterlyGrowth", RAW, "qs:defaultKeyStatistics.earningsQuarterlyGrowth"),
            clustered("shortInterest", "statistics.shortInterest.sharesShort", RAW, "qs:defaultKeyStatistics.sharesShort"),
            clustered("shortInterest", "statistics.shortInterest.shortRatio", RAW, "qs:defaultKeyStatistics.shortRatio"),
            clustered("shortInterest", "statistics.shortInterest.date", EPOCH_DATE, "qs:defaultKeyStatistics.dateShortInterest"),
            clustered("shortInterest", "statistics.shortInterest.sharesShortPriorMonth", RAW, "qs:defaultKeyStatistics.sharesShortPriorMonth"),
            clustered("shortInterest", "statistics.shortInterest.percentSharesOut", RAW, "qs:defaultKeyStatistics.sharesPercentSharesOut"),
            optional("statistics.shortInterest.percentOfFloat", RAW, "qs:defaultKeyStatistics.shortPercentOfFloat"),
            clustered("lastSplit", "statistics.lastSplit.date", EPOCH_DATE, "qs:defaultKeyStatistics.lastSplitDate"),
            clustered("lastSplit", "statistics.lastSplit.factor", RAW, "qs:defaultKeyStatistics.lastSplitFactor"),
            clustered("lastDividend", "statistics.lastDividend.value", RAW, "qs:defaultKeyStatistics.lastDividendValue"),
            clustered("lastDividend", "statistics.lastDividend.date", EPOCH_DATE, "qs:defaultKeyStatistics.lastDividendDate"),
            optional("statistics.exDividendDate", EPOCH_DATE, "qs:summaryDetail.exDividendDate", "qs:calendarEvents.exDividendDate"),
            optional("statistics.fiveYearAvgDividendYield", PERCENT, "qs:summaryDetail.fiveYearAvgDividendYield"));

    private static final List<FieldSpec> FINANCIALS = List.of(
            required("financials.currentPrice", RAW, "qs:financialData.currentPrice"),
            required("financials.totalRevenue", RAW, "qs:financialData.totalRevenue"),
            required("financials.revenuePerShare", RAW, "qs:financialData.revenuePerShare"),
            required("financials.grossProfits", RAW, "qs:financialData.grossProfits"),
            required("financials.margins.gross", RAW, "qs:financialData.grossMargins"),
            required("financials.margins.operating", RAW, "qs:financialData.operatingMargins"),
            required("financials.margins.ebitda", RAW, "qs:financialData.ebitdaMargins"),
            required("financials.totalCash", RAW, "qs:financialData.totalCash"),
            required("financials.totalCashPerShare", RAW, "qs:financialData.totalCashPerShare"),
            required("financials.totalDebt", RAW, "qs:financialData.totalDebt"),
            optional("financials.revenueGrowth", RAW, "qs:financialData.revenueGrowth"),
            optional("financials.debtToEquity", PERCENT, "qs:financialData.debtToEquity"),
            optional("financials.ebitda", RAW, "qs:financialData.ebitda"),
            optional("financials.freeCashflow", RAW, "qs:financialData.freeCashflow"),
            optional("financials.operatingCashflow", RAW, "qs:financialData.operatingCashflow"),
            optional("financials.returnOnEquity", RAW, "qs:financialData.returnOnEquity"),
            optional("financials.returnOnAssets", RAW, "qs:financialData.returnOnAssets"),
            clustered("liquidity", "financials.liquidity.currentRatio", RAW, "qs:financialData.currentRatio"),
            clustered("liquidity", "financials.liquidity.quickRatio", RAW, "qs:financialData.quickRatio"),
            optional("financials.earningsGrowth", RAW, "qs:financialData.earningsGrowth"));

    private static final List<FieldSpec> ANALYSTS = List.of(
            required("analysts.recommendationKey", RAW, "qs:financialData.recommendationKey"),
            clustered("targets", "analysts.targets.low", RAW, "qs:financialData.targetLowPrice"),
            clustered("targets", "analysts.targets.mean", RAW, "qs:financialData.targetMeanPrice"),
            clustered("targets", "analysts.targets.median", RAW, "qs:financialData.targetMedianPrice"),
            clustered("targets", "analysts.targets.high", RAW, "qs:financialData.targetHighPrice"),
            clustered("targets", "analysts.targets.analystCount", RAW, "qs:financialData.numberOfAnalystOpinions"),
            // Sole member of the "rating" cluster: the v7 row is often absent when detail is
            // fetched from quoteSummary alone, and must not empty the whole rating (see brief).
            clustered("rating", "analysts.rating.mean", RAW, "qs:financialData.recommendationMean"),
            optional("analysts.rating.averageAnalystRating", RAW, "v7:averageAnalystRating"),
            list("analysts.recommendationTrend", "qs:recommendationTrend.trend"),
            list("analysts.earningsHistory", "qs:earningsHistory.history"),
            list("analysts.earningsEstimates", "qs:earningsTrend.trend"),
            list("analysts.revenueEstimates", "qs:earningsTrend.trend"),
            list("analysts.epsTrend", "qs:earningsTrend.trend"),
            list("analysts.epsRevisions", "qs:earningsTrend.trend"),
            list("analysts.growthEstimates", "qs:earningsTrend.trend"),
            list("analysts.upgradesDowngrades", "qs:upgradeDowngradeHistory.history"),
            list("analysts.secFilings", "qs:secFilings.filings"));

    private static final List<FieldSpec> OWNERSHIP = List.of(
            required("ownership.breakdown.insidersPercentHeld", RAW, "qs:majorHoldersBreakdown.insidersPercentHeld"),
            required("ownership.breakdown.institutionsPercentHeld", RAW, "qs:majorHoldersBreakdown.institutionsPercentHeld"),
            required("ownership.breakdown.institutionsFloatPercentHeld", RAW, "qs:majorHoldersBreakdown.institutionsFloatPercentHeld"),
            required("ownership.breakdown.institutionsCount", RAW, "qs:majorHoldersBreakdown.institutionsCount"),
            list("ownership.institutions", "qs:institutionOwnership.ownershipList"),
            list("ownership.funds", "qs:fundOwnership.ownershipList"),
            list("ownership.insiders", "qs:insiderHolders.holders"),
            list("ownership.insiderTransactions", "qs:insiderTransactions.transactions"),
            // The object itself is read via the module node (RowMappers.netSharePurchaseActivity);
            // this path only guards presence of the row that identifies it.
            required("ownership.netSharePurchaseActivity", RAW, "qs:netSharePurchaseActivity.period"));

    public static final List<FieldSpec> DETAIL = Specs.concat(PROFILE, STATISTICS, FINANCIALS, ANALYSTS, OWNERSHIP);
}
