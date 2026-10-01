package io.github.dimazigel.yfinance.screener;

/**
 * The fields mutual funds can be screened and sorted by: Yahoo's own catalogue
 * ({@code /v1/finance/screener/instrument/mutualfund/fields}), less the fields it marks
 * deprecated, premium (they need a paid Yahoo subscription), per-locale rankings and non-screenable.
 * A constant is named after Yahoo's field id, which is also what Python yfinance's queries use;
 * its comment is Yahoo's display name and category.
 */
public enum FundScreenField implements ScreenField {
    /** Annual Report Gross Expense Ratio. (Fees And Expenses) */
    ANNUALREPORTGROSSEXPENSERATIO("annualreportgrossexpenseratio", Type.NUMBER),
    /** Annual Report Net Expense Ratio. (Fees And Expenses) */
    ANNUALREPORTNETEXPENSERATIO("annualreportnetexpenseratio", Type.NUMBER),
    /** Annual Return NAV Year 1. (Historical Performance) */
    ANNUALRETURNNAVY1("annualreturnnavy1", Type.NUMBER),
    /** Annual Return NAV Year 1 Category Rank. (Historical Performance) */
    ANNUALRETURNNAVY1CATEGORYRANK("annualreturnnavy1categoryrank", Type.NUMBER),
    /** Annual Return NAV Year 3. (Historical Performance) */
    ANNUALRETURNNAVY3("annualreturnnavy3", Type.NUMBER),
    /** Annual Return NAV Year 5. (Historical Performance) */
    ANNUALRETURNNAVY5("annualreturnnavy5", Type.NUMBER),
    /** Funds by Category. (Fundamentals) */
    CATEGORYNAME("categoryname", Type.STRING),
    /** EOD Price. (Share Statistics) */
    EODPRICE("eodprice", Type.NUMBER),
    /** Exchange. (Profile) */
    EXCHANGE("exchange", Type.STRING),
    /** 52 Week Percent Change. (Share Statistics) */
    FIFTYTWOWKPERCENTCHANGE("fiftytwowkpercentchange", Type.NUMBER),
    /** Funds by Company. (Fundamentals) */
    FUNDFAMILYNAME("fundfamilyname", Type.STRING),
    /** Fund Net Assets. (Fundamentals) */
    FUNDNETASSETS("fundnetassets", Type.NUMBER),
    /** Initial Minimum Investment. (Purchase Details) */
    INITIALINVESTMENT("initialinvestment", Type.NUMBER),
    /** Intraday Price. (Share Statistics) */
    INTRADAYPRICE("intradayprice", Type.NUMBER),
    /** Change. (Share Statistics) */
    INTRADAYPRICECHANGE("intradaypricechange", Type.NUMBER),
    /** Market Capital Value - Long. (Portfolio Statistics) */
    MARKETCAPITALVALUELONG("marketcapitalvaluelong", Type.NUMBER),
    /** Page Views Weekly Growth. (User Insights) */
    PAGE_VIEW_GROWTH_WEEKLY("page_view_growth_weekly", Type.NUMBER),
    /** Percent Change. (Share Statistics) */
    PERCENTCHANGE("percentchange", Type.NUMBER),
    /** Morningstar Performance Rating Overall. (Trailing Performance) */
    PERFORMANCERATINGOVERALL("performanceratingoverall", Type.NUMBER),
    /** Primary Sector. (Fundamentals) */
    PRIMARY_SECTOR("primary_sector", Type.STRING),
    /** Quarter End Trailing Return YTD. (Trailing Performance) */
    QUARTERENDTRAILINGRETURNYTD("quarterendtrailingreturnytd", Type.NUMBER),
    /** Region. (Share Statistics) */
    REGION("region", Type.STRING),
    /** Morningstar Risk Rating Overall. (Trailing Performance) */
    RISKRATINGOVERALL("riskratingoverall", Type.NUMBER),
    /** Symbol. (Fundamentals) */
    TICKER("ticker", Type.STRING),
    /** Trailing 3 Months Return. (Trailing Performance) */
    TRAILING_3M_RETURN("trailing_3m_return", Type.NUMBER),
    /** Trailing YTD Return. (Trailing Performance) */
    TRAILING_YTD_RETURN("trailing_ytd_return", Type.NUMBER),
    /** Turnover Ratio. (Fees And Expenses) */
    TURNOVERRATIO("turnoverratio", Type.NUMBER);

    private final String key;
    private final Type type;

    FundScreenField(String key, Type type) {
        this.key = key;
        this.type = type;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Type type() {
        return type;
    }
}
