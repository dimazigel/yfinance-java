package io.github.dimazigel.yfinance.enums;

import java.util.Collection;
import java.util.stream.Collectors;

/** Modules available on the {@code /v10/finance/quoteSummary} endpoint. */
public enum QuoteSummaryModule implements WireEnum {
    ASSET_PROFILE("assetProfile"),
    SUMMARY_PROFILE("summaryProfile"),
    SUMMARY_DETAIL("summaryDetail"),
    QUOTE_TYPE("quoteType"),
    PRICE("price"),
    FINANCIAL_DATA("financialData"),
    DEFAULT_KEY_STATISTICS("defaultKeyStatistics"),
    CALENDAR_EVENTS("calendarEvents"),
    SEC_FILINGS("secFilings"),
    ESG_SCORES("esgScores"),
    RECOMMENDATION_TREND("recommendationTrend"),
    UPGRADE_DOWNGRADE_HISTORY("upgradeDowngradeHistory"),
    INSTITUTION_OWNERSHIP("institutionOwnership"),
    FUND_OWNERSHIP("fundOwnership"),
    MAJOR_HOLDERS_BREAKDOWN("majorHoldersBreakdown"),
    INSIDER_HOLDERS("insiderHolders"),
    INSIDER_TRANSACTIONS("insiderTransactions"),
    NET_SHARE_PURCHASE_ACTIVITY("netSharePurchaseActivity"),
    EARNINGS("earnings"),
    EARNINGS_HISTORY("earningsHistory"),
    EARNINGS_TREND("earningsTrend"),
    INDUSTRY_TREND("industryTrend"),
    FINANCIALS_TEMPLATE("financialsTemplate");

    private final String wireValue;

    QuoteSummaryModule(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static QuoteSummaryModule fromWire(String wire) {
        return WireEnum.fromWire(values(), wire, "module");
    }

    /** Joins a collection of modules into the comma-separated {@code modules} query value. */
    public static String toQueryParam(Collection<QuoteSummaryModule> modules) {
        return modules.stream().map(QuoteSummaryModule::wireValue).collect(Collectors.joining(","));
    }
}
