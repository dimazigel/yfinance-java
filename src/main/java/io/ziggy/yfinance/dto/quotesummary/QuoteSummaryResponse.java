package io.ziggy.yfinance.dto.quotesummary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ziggy.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.List;

/** Raw deserialization of the {@code /v10/finance/quoteSummary} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteSummaryResponse(QuoteSummary quoteSummary) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteSummary(List<Result> result, Error error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String code, String description) implements YahooError {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            AssetProfile assetProfile,
            QuoteType quoteType,
            Price price,
            SummaryDetail summaryDetail,
            FinancialData financialData,
            DefaultKeyStatistics defaultKeyStatistics,
            CalendarEvents calendarEvents,
            SecFilings secFilings,
            RecommendationTrend recommendationTrend,
            UpgradeDowngradeHistory upgradeDowngradeHistory,
            MajorHoldersBreakdown majorHoldersBreakdown,
            Ownership institutionOwnership,
            Ownership fundOwnership,
            InsiderTransactions insiderTransactions,
            InsiderHolders insiderHolders,
            NetSharePurchaseActivity netSharePurchaseActivity,
            EarningsHistory earningsHistory,
            EarningsTrend earningsTrend) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssetProfile(
            String address1,
            String city,
            String state,
            String zip,
            String country,
            String phone,
            String website,
            String industry,
            String sector,
            String longBusinessSummary,
            Integer fullTimeEmployees,
            List<Officer> companyOfficers) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Officer(String name, String title, Integer age, Long totalPay) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteType(
            String exchange,
            String quoteType,
            String symbol,
            String longName,
            String shortName,
            String timeZoneFullName,
            String timeZoneShortName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(
            String currency,
            String marketState,
            BigDecimal regularMarketPrice,
            BigDecimal regularMarketChange,
            BigDecimal regularMarketChangePercent,
            BigDecimal regularMarketPreviousClose,
            BigDecimal marketCap,
            String exchangeName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SummaryDetail(
            BigDecimal previousClose,
            BigDecimal open,
            BigDecimal dayLow,
            BigDecimal dayHigh,
            Long volume,
            BigDecimal fiftyTwoWeekLow,
            BigDecimal fiftyTwoWeekHigh,
            BigDecimal trailingPE,
            BigDecimal dividendYield,
            String currency) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FinancialData(
            BigDecimal currentPrice,
            BigDecimal targetMeanPrice,
            BigDecimal targetLowPrice,
            BigDecimal targetHighPrice,
            BigDecimal targetMedianPrice,
            BigDecimal recommendationMean,
            String recommendationKey,
            Integer numberOfAnalystOpinions,
            BigDecimal totalRevenue,
            BigDecimal profitMargins) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DefaultKeyStatistics(
            BigDecimal trailingEps,
            BigDecimal forwardEps,
            BigDecimal bookValue,
            BigDecimal priceToBook,
            Long sharesOutstanding,
            BigDecimal beta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CalendarEvents(Earnings earnings) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Earnings(
                List<Long> earningsDate,
                BigDecimal earningsAverage,
                BigDecimal earningsLow,
                BigDecimal earningsHigh,
                BigDecimal revenueAverage) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecFilings(List<Filing> filings) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Filing(String date, String type, String title, String edgarUrl, Long epochDate) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecommendationTrend(List<Trend> trend) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Trend(String period, Integer strongBuy, Integer buy, Integer hold, Integer sell, Integer strongSell) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpgradeDowngradeHistory(List<History> history) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record History(Long epochGradeDate, String firm, String toGrade, String fromGrade, String action) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MajorHoldersBreakdown(
            BigDecimal insidersPercentHeld,
            BigDecimal institutionsPercentHeld,
            BigDecimal institutionsFloatPercentHeld,
            Integer institutionsCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ownership(List<OwnershipRow> ownershipList) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record OwnershipRow(
                Long reportDate,
                String organization,
                BigDecimal pctHeld,
                Long position,
                Long value,
                BigDecimal pctChange) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InsiderTransactions(List<TransactionRow> transactions) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TransactionRow(
                Long startDate,
                String filerName,
                String filerRelation,
                String transactionText,
                Long shares,
                Long value) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InsiderHolders(List<InsiderHolderRow> holders) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record InsiderHolderRow(
                String name,
                String relation,
                String transactionDescription,
                Long latestTransDate,
                Long positionDirect,
                Long positionDirectDate) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NetSharePurchaseActivity(
            String period,
            Integer buyInfoCount,
            Long buyInfoShares,
            BigDecimal buyPercentInsiderShares,
            Integer sellInfoCount,
            Long sellInfoShares,
            BigDecimal sellPercentInsiderShares,
            Integer netInfoCount,
            Long netInfoShares,
            BigDecimal netPercentInsiderShares,
            Long totalInsiderShares) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EarningsHistory(List<HistoryRow> history) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record HistoryRow(
                BigDecimal epsActual,
                BigDecimal epsEstimate,
                BigDecimal epsDifference,
                BigDecimal surprisePercent,
                Long quarter,
                String period) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EarningsTrend(List<TrendRow> trend) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TrendRow(
                String period,
                String endDate,
                BigDecimal growth,
                Estimate earningsEstimate,
                Estimate revenueEstimate,
                EpsTrendRow epsTrend,
                EpsRevisionsRow epsRevisions) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Estimate(BigDecimal avg, BigDecimal low, BigDecimal high, Integer numberOfAnalysts, BigDecimal yearAgoEps) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record EpsTrendRow(
                BigDecimal current,
                @JsonProperty("7daysAgo") BigDecimal sevenDaysAgo,
                @JsonProperty("30daysAgo") BigDecimal thirtyDaysAgo,
                @JsonProperty("60daysAgo") BigDecimal sixtyDaysAgo,
                @JsonProperty("90daysAgo") BigDecimal ninetyDaysAgo) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record EpsRevisionsRow(
                @JsonProperty("upLast7days") Integer upLast7Days,
                @JsonProperty("upLast30days") Integer upLast30Days,
                @JsonProperty("downLast30days") Integer downLast30Days,
                @JsonProperty("downLast90days") Integer downLast90Days) {}
    }
}
