package io.github.dimazigel.yfinance.detail;

import io.github.dimazigel.yfinance.detail.rows.EarningsHistoryEntry;
import io.github.dimazigel.yfinance.detail.rows.EpsRevisionsPeriod;
import io.github.dimazigel.yfinance.detail.rows.EpsTrendPeriod;
import io.github.dimazigel.yfinance.detail.rows.GrowthEstimate;
import io.github.dimazigel.yfinance.detail.rows.InsiderHolder;
import io.github.dimazigel.yfinance.detail.rows.InsiderTransaction;
import io.github.dimazigel.yfinance.detail.rows.InstitutionalHolder;
import io.github.dimazigel.yfinance.detail.rows.PeriodEstimate;
import io.github.dimazigel.yfinance.detail.rows.RecommendationPeriod;
import io.github.dimazigel.yfinance.detail.rows.SecFiling;
import io.github.dimazigel.yfinance.detail.rows.UpgradeDowngrade;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Equity detail: company profile, statistics, financial health, analyst view and ownership,
 * assembled from the equity quoteSummary modules.
 */
public record EquityDetail(
        Symbol symbol,
        CompanyProfile profile,
        Statistics statistics,
        FinancialHealth financials,
        AnalystView analysts,
        Ownership ownership,
        Instant fetchedAt) {

    /** Company profile from the {@code assetProfile}/{@code summaryProfile} modules. */
    public record CompanyProfile(
            String sector,
            String industry,
            String country,
            String city,
            String address1,
            String zip,
            URI website,
            String longBusinessSummary,
            List<Officer> officers,
            Optional<Integer> fullTimeEmployees,
            Optional<String> phone,
            Optional<String> state,
            Optional<URI> irWebsite,
            Optional<Governance> governance) {

        public CompanyProfile {
            officers = List.copyOf(officers);
        }
    }

    /** A named company officer. */
    public record Officer(String name, String title, Optional<Integer> age, Optional<Long> totalPay) {}

    /** Board and management governance risk scores (1 lowest risk, 10 highest). */
    public record Governance(
            int auditRisk, int boardRisk, int compensationRisk, int shareholderRightsRisk, int overallRisk) {}

    /** Ownership and valuation statistics from {@code defaultKeyStatistics} and related modules. */
    public record Statistics(
            long floatShares,
            BigDecimal heldPercentInsiders,
            BigDecimal heldPercentInstitutions,
            BigDecimal profitMargins,
            Optional<BigDecimal> beta,
            Optional<BigDecimal> enterpriseValue,
            Optional<BigDecimal> enterpriseToRevenue,
            Optional<BigDecimal> enterpriseToEbitda,
            Optional<FiscalCalendar> fiscal,
            Optional<BigDecimal> pegRatio,
            Optional<BigDecimal> payoutRatio,
            Optional<BigDecimal> priceToSales,
            Optional<BigDecimal> earningsQuarterlyGrowth,
            Optional<ShortInterest> shortInterest,
            Optional<LastSplit> lastSplit,
            Optional<LastDividend> lastDividend,
            Optional<LocalDate> exDividendDate,
            Optional<BigDecimal> fiveYearAvgDividendYield) {}

    /** The issuer's fiscal calendar. */
    public record FiscalCalendar(LocalDate lastFiscalYearEnd, LocalDate nextFiscalYearEnd, LocalDate mostRecentQuarter) {}

    /** A short-interest snapshot as of {@link #date()}. */
    public record ShortInterest(
            long sharesShort,
            BigDecimal shortRatio,
            LocalDate date,
            long sharesShortPriorMonth,
            BigDecimal percentSharesOut,
            Optional<BigDecimal> percentOfFloat) {}

    /** The issuer's most recent stock split. */
    public record LastSplit(LocalDate date, String factor) {}

    /** The issuer's most recent dividend payment. */
    public record LastDividend(BigDecimal value, LocalDate date) {}

    /** Financial health metrics from the {@code financialData} module. */
    public record FinancialHealth(
            BigDecimal currentPrice,
            BigDecimal totalRevenue,
            BigDecimal revenuePerShare,
            BigDecimal grossProfits,
            Margins margins,
            BigDecimal totalCash,
            BigDecimal totalCashPerShare,
            BigDecimal totalDebt,
            Optional<BigDecimal> revenueGrowth,
            Optional<BigDecimal> debtToEquity,
            Optional<BigDecimal> ebitda,
            Optional<BigDecimal> freeCashflow,
            Optional<BigDecimal> operatingCashflow,
            Optional<BigDecimal> returnOnEquity,
            Optional<BigDecimal> returnOnAssets,
            Optional<Liquidity> liquidity,
            Optional<BigDecimal> earningsGrowth) {}

    /** Profit margins as fractions of revenue. */
    public record Margins(BigDecimal gross, BigDecimal operating, BigDecimal ebitda) {}

    /** Short-term liquidity ratios. */
    public record Liquidity(BigDecimal currentRatio, BigDecimal quickRatio) {}

    /** Analyst coverage: recommendations, price targets, estimates and rating history. */
    public record AnalystView(
            String recommendationKey,
            Optional<Targets> targets,
            Optional<Rating> rating,
            List<RecommendationPeriod> recommendationTrend,
            List<EarningsHistoryEntry> earningsHistory,
            List<PeriodEstimate> earningsEstimates,
            List<PeriodEstimate> revenueEstimates,
            List<EpsTrendPeriod> epsTrend,
            List<EpsRevisionsPeriod> epsRevisions,
            List<GrowthEstimate> growthEstimates,
            List<UpgradeDowngrade> upgradesDowngrades,
            List<SecFiling> secFilings) {

        public AnalystView {
            recommendationTrend = List.copyOf(recommendationTrend);
            earningsHistory = List.copyOf(earningsHistory);
            earningsEstimates = List.copyOf(earningsEstimates);
            revenueEstimates = List.copyOf(revenueEstimates);
            epsTrend = List.copyOf(epsTrend);
            epsRevisions = List.copyOf(epsRevisions);
            growthEstimates = List.copyOf(growthEstimates);
            upgradesDowngrades = List.copyOf(upgradesDowngrades);
            secFilings = List.copyOf(secFilings);
        }
    }

    /** Consensus analyst price targets. */
    public record Targets(BigDecimal low, BigDecimal mean, BigDecimal median, BigDecimal high, int analystCount) {}

    /**
     * Consensus analyst rating (1 strong buy, 5 strong sell). The display string Yahoo pairs with it
     * ({@code "2.2 - Buy"}) is a v7 field and lives on the snapshot: {@code Equity.averageAnalystRating()}.
     */
    public record Rating(BigDecimal mean) {}

    /** Ownership breakdown, institutional/insider holders and net insider activity. */
    public record Ownership(
            Breakdown breakdown,
            List<InstitutionalHolder> institutions,
            List<InstitutionalHolder> funds,
            List<InsiderHolder> insiders,
            List<InsiderTransaction> insiderTransactions,
            NetSharePurchaseActivity netSharePurchaseActivity) {

        public Ownership {
            institutions = List.copyOf(institutions);
            funds = List.copyOf(funds);
            insiders = List.copyOf(insiders);
            insiderTransactions = List.copyOf(insiderTransactions);
        }
    }

    /** Aggregate ownership percentages from the {@code majorHoldersBreakdown} module. */
    public record Breakdown(
            BigDecimal insidersPercentHeld,
            BigDecimal institutionsPercentHeld,
            BigDecimal institutionsFloatPercentHeld,
            int institutionsCount) {}

    /** Aggregated insider buy/sell activity over a trailing period (e.g. {@code 6m}). */
    public record NetSharePurchaseActivity(
            String period,
            Optional<Integer> buyCount,
            Optional<Long> buyShares,
            Optional<Integer> sellCount,
            Optional<Long> sellShares,
            Optional<Integer> netCount,
            Optional<Long> netShares,
            Optional<Long> totalInsiderShares) {}
}
