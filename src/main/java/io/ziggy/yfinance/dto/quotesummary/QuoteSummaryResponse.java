package io.ziggy.yfinance.dto.quotesummary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ziggy.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Raw deserialization of the {@code /v10/finance/quoteSummary} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteSummaryResponse(@Nullable QuoteSummary quoteSummary) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteSummary(@Nullable List<Result> result, @Nullable Error error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(@Nullable String code, @Nullable String description) implements YahooError {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @Nullable AssetProfile assetProfile,
            @Nullable QuoteType quoteType,
            @Nullable Price price,
            @Nullable SummaryDetail summaryDetail,
            @Nullable FinancialData financialData,
            @Nullable DefaultKeyStatistics defaultKeyStatistics,
            @Nullable CalendarEvents calendarEvents,
            @Nullable SecFilings secFilings,
            @Nullable RecommendationTrend recommendationTrend,
            @Nullable UpgradeDowngradeHistory upgradeDowngradeHistory,
            @Nullable MajorHoldersBreakdown majorHoldersBreakdown,
            @Nullable Ownership institutionOwnership,
            @Nullable Ownership fundOwnership,
            @Nullable InsiderTransactions insiderTransactions,
            @Nullable InsiderHolders insiderHolders,
            @Nullable NetSharePurchaseActivity netSharePurchaseActivity,
            @Nullable EarningsHistory earningsHistory,
            @Nullable EarningsTrend earningsTrend) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssetProfile(
            @Nullable String address1,
            @Nullable String city,
            @Nullable String state,
            @Nullable String zip,
            @Nullable String country,
            @Nullable String phone,
            @Nullable String website,
            @Nullable String industry,
            @Nullable String sector,
            @Nullable String longBusinessSummary,
            @Nullable Integer fullTimeEmployees,
            @Nullable List<Officer> companyOfficers) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Officer(@Nullable String name, @Nullable String title, @Nullable Integer age, @Nullable Long totalPay) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteType(
            @Nullable String exchange,
            @Nullable String quoteType,
            @Nullable String symbol,
            @Nullable String longName,
            @Nullable String shortName,
            @Nullable String timeZoneFullName,
            @Nullable String timeZoneShortName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(
            @Nullable String currency,
            @Nullable String marketState,
            @Nullable BigDecimal regularMarketPrice,
            @Nullable BigDecimal regularMarketChange,
            @Nullable BigDecimal regularMarketChangePercent,
            @Nullable BigDecimal regularMarketPreviousClose,
            @Nullable BigDecimal marketCap,
            @Nullable String exchangeName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SummaryDetail(
            @Nullable BigDecimal previousClose,
            @Nullable BigDecimal open,
            @Nullable BigDecimal dayLow,
            @Nullable BigDecimal dayHigh,
            @Nullable Long volume,
            @Nullable BigDecimal fiftyTwoWeekLow,
            @Nullable BigDecimal fiftyTwoWeekHigh,
            @Nullable BigDecimal trailingPE,
            @Nullable BigDecimal dividendYield,
            @Nullable String currency) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FinancialData(
            @Nullable BigDecimal currentPrice,
            @Nullable BigDecimal targetMeanPrice,
            @Nullable BigDecimal targetLowPrice,
            @Nullable BigDecimal targetHighPrice,
            @Nullable BigDecimal targetMedianPrice,
            @Nullable BigDecimal recommendationMean,
            @Nullable String recommendationKey,
            @Nullable Integer numberOfAnalystOpinions,
            @Nullable BigDecimal totalRevenue,
            @Nullable BigDecimal profitMargins) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DefaultKeyStatistics(
            @Nullable BigDecimal trailingEps,
            @Nullable BigDecimal forwardEps,
            @Nullable BigDecimal bookValue,
            @Nullable BigDecimal priceToBook,
            @Nullable Long sharesOutstanding,
            @Nullable BigDecimal beta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CalendarEvents(@Nullable Earnings earnings) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Earnings(
                @Nullable List<Long> earningsDate,
                @Nullable BigDecimal earningsAverage,
                @Nullable BigDecimal earningsLow,
                @Nullable BigDecimal earningsHigh,
                @Nullable BigDecimal revenueAverage) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecFilings(@Nullable List<Filing> filings) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Filing(@Nullable String date, @Nullable String type, @Nullable String title, @Nullable String edgarUrl, @Nullable Long epochDate) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecommendationTrend(@Nullable List<Trend> trend) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Trend(@Nullable String period, @Nullable Integer strongBuy, @Nullable Integer buy, @Nullable Integer hold, @Nullable Integer sell, @Nullable Integer strongSell) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpgradeDowngradeHistory(@Nullable List<History> history) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record History(@Nullable Long epochGradeDate, @Nullable String firm, @Nullable String toGrade, @Nullable String fromGrade, @Nullable String action) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MajorHoldersBreakdown(
            @Nullable BigDecimal insidersPercentHeld,
            @Nullable BigDecimal institutionsPercentHeld,
            @Nullable BigDecimal institutionsFloatPercentHeld,
            @Nullable Integer institutionsCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ownership(@Nullable List<OwnershipRow> ownershipList) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record OwnershipRow(
                @Nullable Long reportDate,
                @Nullable String organization,
                @Nullable BigDecimal pctHeld,
                @Nullable Long position,
                @Nullable Long value,
                @Nullable BigDecimal pctChange) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InsiderTransactions(@Nullable List<TransactionRow> transactions) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TransactionRow(
                @Nullable Long startDate,
                @Nullable String filerName,
                @Nullable String filerRelation,
                @Nullable String transactionText,
                @Nullable Long shares,
                @Nullable Long value) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InsiderHolders(@Nullable List<InsiderHolderRow> holders) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record InsiderHolderRow(
                @Nullable String name,
                @Nullable String relation,
                @Nullable String transactionDescription,
                @Nullable Long latestTransDate,
                @Nullable Long positionDirect,
                @Nullable Long positionDirectDate) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NetSharePurchaseActivity(
            @Nullable String period,
            @Nullable Integer buyInfoCount,
            @Nullable Long buyInfoShares,
            @Nullable BigDecimal buyPercentInsiderShares,
            @Nullable Integer sellInfoCount,
            @Nullable Long sellInfoShares,
            @Nullable BigDecimal sellPercentInsiderShares,
            @Nullable Integer netInfoCount,
            @Nullable Long netInfoShares,
            @Nullable BigDecimal netPercentInsiderShares,
            @Nullable Long totalInsiderShares) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EarningsHistory(@Nullable List<HistoryRow> history) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record HistoryRow(
                @Nullable BigDecimal epsActual,
                @Nullable BigDecimal epsEstimate,
                @Nullable BigDecimal epsDifference,
                @Nullable BigDecimal surprisePercent,
                @Nullable Long quarter,
                @Nullable String period) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EarningsTrend(@Nullable List<TrendRow> trend) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TrendRow(
                @Nullable String period,
                @Nullable String endDate,
                @Nullable BigDecimal growth,
                @Nullable Estimate earningsEstimate,
                @Nullable Estimate revenueEstimate,
                @Nullable EpsTrendRow epsTrend,
                @Nullable EpsRevisionsRow epsRevisions) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Estimate(@Nullable BigDecimal avg, @Nullable BigDecimal low, @Nullable BigDecimal high, @Nullable Integer numberOfAnalysts, @Nullable BigDecimal yearAgoEps) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record EpsTrendRow(
                @Nullable BigDecimal current,
                @JsonProperty("7daysAgo") @Nullable BigDecimal sevenDaysAgo,
                @JsonProperty("30daysAgo") @Nullable BigDecimal thirtyDaysAgo,
                @JsonProperty("60daysAgo") @Nullable BigDecimal sixtyDaysAgo,
                @JsonProperty("90daysAgo") @Nullable BigDecimal ninetyDaysAgo) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record EpsRevisionsRow(
                @JsonProperty("upLast7days") @Nullable Integer upLast7Days,
                @JsonProperty("upLast30days") @Nullable Integer upLast30Days,
                @JsonProperty("downLast30days") @Nullable Integer downLast30Days,
                @JsonProperty("downLast90days") @Nullable Integer downLast90Days) {}
    }
}
