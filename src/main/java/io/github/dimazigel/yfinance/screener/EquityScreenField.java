package io.github.dimazigel.yfinance.screener;

/**
 * The fields equities can be screened and sorted by: Yahoo's own catalogue
 * ({@code /v1/finance/screener/instrument/equity/fields}), less the fields it marks
 * deprecated, premium (they need a paid Yahoo subscription), per-locale rankings and non-screenable.
 * A constant is named after Yahoo's field id, which is also what Python yfinance's queries use;
 * its comment is Yahoo's display name and category.
 */
public enum EquityScreenField implements ScreenField {
    /** All Time High(As of Date). (Signals) */
    ALL_TIME_HIGH_DATETIME("all_time_high.datetime", Type.STRING),
    /** All Time High. (Signals) */
    ALL_TIME_HIGH_VALUE("all_time_high.value", Type.NUMBER),
    /** Altman Z Score Using the Average Stock Information for a Period. (Share Statistics) */
    ALTMANZSCOREUSINGTHEAVERAGESTOCKINFORMATIONFORAPERIOD_LASTTWELVEMONTHS("altmanzscoreusingtheaveragestockinformationforaperiod.lasttwelvemonths", Type.NUMBER),
    /** Avg. Analyst Rating. (User Insights) */
    AVERAGE_ANALYST_RATING("average_analyst_rating", Type.STRING),
    /** Average Daily 3m Volume. (Share Statistics) */
    AVGDAILYVOL3M("avgdailyvol3m", Type.NUMBER),
    /** Basic EPS - Continuing Operations. (Income) */
    BASICEPSCONTINUINGOPERATIONS_LASTTWELVEMONTHS("basicepscontinuingoperations.lasttwelvemonths", Type.NUMBER),
    /** Beta. (Share Statistics) */
    BETA("beta", Type.NUMBER),
    /** Book Value / Share. (Valuation Measures) */
    BOOKVALUESHARE_LASTTWELVEMONTHS("bookvalueshare.lasttwelvemonths", Type.NUMBER),
    /** Capital Expenditure. (Cash Flow Statement) */
    CAPITALEXPENDITURE_LASTTWELVEMONTHS("capitalexpenditure.lasttwelvemonths", Type.NUMBER),
    /** Cash on Hand(3m). (Balance Sheet) */
    CASH_ON_HAND_QUARTERLY("cash_on_hand_quarterly", Type.NUMBER),
    /** Cash on Hand(3m). (Balance Sheet) */
    CASH_ON_HAND_QUARTERLY_MARKET_CURRENCY("cash_on_hand_quarterly_market_currency", Type.NUMBER),
    /** Cash from Operations. (Cash Flow Statement) */
    CASHFROMOPERATIONS_LASTTWELVEMONTHS("cashfromoperations.lasttwelvemonths", Type.NUMBER),
    /** Cash From Operations, 1 Yr. Growth %. (Cash Flow Statement) */
    CASHFROMOPERATIONS1YRGROWTH_LASTTWELVEMONTHS("cashfromoperations1yrgrowth.lasttwelvemonths", Type.NUMBER),
    /** Consecutive Years of Dividend Growth Count. (Dividends and Splits) */
    CONSECUTIVE_YEARS_OF_DIVIDEND_GROWTH_COUNT("consecutive_years_of_dividend_growth_count", Type.NUMBER),
    /** Current Ratio. (Valuation Measures) */
    CURRENTRATIO_LASTTWELVEMONTHS("currentratio.lasttwelvemonths", Type.NUMBER),
    /** Short Interest Ratio. (Short Interest) */
    DAYS_TO_COVER_SHORT_VALUE("days_to_cover_short.value", Type.NUMBER),
    /** Day Volume. (Share Statistics) */
    DAYVOLUME("dayvolume", Type.NUMBER),
    /** Diluted EPS, 1 Yr. Growth %. (Income) */
    DILUTEDEPS1YRGROWTH_LASTTWELVEMONTHS("dilutedeps1yrgrowth.lasttwelvemonths", Type.NUMBER),
    /** Diluted EPS - Continuing Operations. (Income) */
    DILUTEDEPSCONTINUINGOPERATIONS_LASTTWELVEMONTHS("dilutedepscontinuingoperations.lasttwelvemonths", Type.NUMBER),
    /** Dividend Yield. (Ratios) */
    DIVIDENDYIELD("dividendyield", Type.NUMBER),
    /** EBIT. (Income) */
    EBIT_LASTTWELVEMONTHS("ebit.lasttwelvemonths", Type.NUMBER),
    /** EBITDA. (Income) */
    EBITDA_LASTTWELVEMONTHS("ebitda.lasttwelvemonths", Type.NUMBER),
    /** EBITDA, 1 Yr. Growth %. (Income) */
    EBITDA1YRGROWTH_LASTTWELVEMONTHS("ebitda1yrgrowth.lasttwelvemonths", Type.NUMBER),
    /** EBITDA / Interest Expense. (Valuation Measures) */
    EBITDAINTERESTEXPENSE_LASTTWELVEMONTHS("ebitdainterestexpense.lasttwelvemonths", Type.NUMBER),
    /** EBITDA Margin %. (Valuation Measures) */
    EBITDAMARGIN_LASTTWELVEMONTHS("ebitdamargin.lasttwelvemonths", Type.NUMBER),
    /** EBIT / Interest Expense. (Valuation Measures) */
    EBITINTERESTEXPENSE_LASTTWELVEMONTHS("ebitinterestexpense.lasttwelvemonths", Type.NUMBER),
    /** Environmental Score. (ESG Scores) */
    ENVIRONMENTAL_SCORE("environmental_score", Type.NUMBER),
    /** EOD Price. (Share Statistics) */
    EODPRICE("eodprice", Type.NUMBER),
    /** EOD Volume. (Share Statistics) */
    EODVOLUME("eodvolume", Type.NUMBER),
    /** EPS Growth. (Earnings) */
    EPSGROWTH_LASTTWELVEMONTHS("epsgrowth.lasttwelvemonths", Type.NUMBER),
    /** ESG Score. (ESG Scores) */
    ESG_SCORE("esg_score", Type.NUMBER),
    /** Exchange. (Profile) */
    EXCHANGE("exchange", Type.STRING),
    /** 52 Week Percent Change. (Share Statistics) */
    FIFTYTWOWKPERCENTCHANGE("fiftytwowkpercentchange", Type.NUMBER),
    /** Financial Currency. (Profitability ratios and Dividends) */
    FINANCIAL_CURRENCY("financialCurrency", Type.STRING),
    /** Forward Dividend Per Share. (Dividends and Splits) */
    FORWARD_DIVIDEND_PER_SHARE("forward_dividend_per_share", Type.NUMBER),
    /** Forward Dividend Yield. (Dividends and Splits) */
    FORWARD_DIVIDEND_YIELD("forward_dividend_yield", Type.NUMBER),
    /** Full Day Change. (Changes in Price and Market Cap) */
    FULL_DAY_CHANGE("full_day_change", Type.NUMBER),
    /** Full Day Percent Change. (Changes in Price and Market Cap) */
    FULL_DAY_CHANGE_PERCENT("full_day_change_percent", Type.NUMBER),
    /** Full Day Price. (Changes in Price and Market Cap) */
    FULL_DAY_PRICE("full_day_price", Type.NUMBER),
    /** Full Time Employees. (Workforce) */
    FULLTIMEEMPLOYEES_ANNUAL("fulltimeemployees.annual", Type.NUMBER),
    /** Governance Score. (ESG Scores) */
    GOVERNANCE_SCORE("governance_score", Type.NUMBER),
    /** Gross Profit. (Income) */
    GROSSPROFIT_LASTTWELVEMONTHS("grossprofit.lasttwelvemonths", Type.NUMBER),
    /** Gross Profit Margin %. (Financial Highlights) */
    GROSSPROFITMARGIN_LASTTWELVEMONTHS("grossprofitmargin.lasttwelvemonths", Type.NUMBER),
    /** Highest Controversy. (ESG Scores) */
    HIGHEST_CONTROVERSY("highest_controversy", Type.NUMBER),
    /** Index Membership. (Index Membership) */
    INDEXMEMBERSHIP("indexmembership", Type.STRING),
    /** Indices. (Indices) */
    INDICES("indices", Type.STRING),
    /** Industry. (Sector &amp; Industry) */
    INDUSTRY("industry", Type.STRING),
    /** Market Cap (Intraday). (Valuation Measures) */
    INTRADAYMARKETCAP("intradaymarketcap", Type.NUMBER),
    /** Intraday Price. (Share Statistics) */
    INTRADAYPRICE("intradayprice", Type.NUMBER),
    /** Change. (Share Statistics) */
    INTRADAYPRICECHANGE("intradaypricechange", Type.NUMBER),
    /** Isin. (Security Mapping) */
    ISIN("isin", Type.STRING),
    /** Last Close 52 Week High. (Share Statistics) */
    LASTCLOSE52WEEKHIGH_LASTTWELVEMONTHS("lastclose52weekhigh.lasttwelvemonths", Type.NUMBER),
    /** Last Close 52 Week Low. (Share Statistics) */
    LASTCLOSE52WEEKLOW_LASTTWELVEMONTHS("lastclose52weeklow.lasttwelvemonths", Type.NUMBER),
    /** Last Close Market Cap. (Share Statistics) */
    LASTCLOSEMARKETCAP_LASTTWELVEMONTHS("lastclosemarketcap.lasttwelvemonths", Type.NUMBER),
    /** Last Close Market Cap / Total Revenue. (Valuation Measures) */
    LASTCLOSEMARKETCAPTOTALREVENUE_LASTTWELVEMONTHS("lastclosemarketcaptotalrevenue.lasttwelvemonths", Type.NUMBER),
    /** Last Close Price / Book Value. (Valuation Measures) */
    LASTCLOSEPRICEBOOKVALUE_LASTTWELVEMONTHS("lastclosepricebookvalue.lasttwelvemonths", Type.NUMBER),
    /** Last Close Price / Earnings. (Share Statistics) */
    LASTCLOSEPRICEEARNINGS_LASTTWELVEMONTHS("lastclosepriceearnings.lasttwelvemonths", Type.NUMBER),
    /** Last Close Price / Tangible Book Value. (Valuation Measures) */
    LASTCLOSEPRICETANGIBLEBOOKVALUE_LASTTWELVEMONTHS("lastclosepricetangiblebookvalue.lasttwelvemonths", Type.NUMBER),
    /** Last Close TEV / EBIT. (Valuation Measures) */
    LASTCLOSETEVEBIT_LASTTWELVEMONTHS("lastclosetevebit.lasttwelvemonths", Type.NUMBER),
    /** Last Close TEV / EBITDA. (Valuation Measures) */
    LASTCLOSETEVEBITDA_LASTTWELVEMONTHS("lastclosetevebitda.lasttwelvemonths", Type.NUMBER),
    /** Last Close TEV / Total Revenue. (Valuation Measures) */
    LASTCLOSETEVTOTALREVENUE_LASTTWELVEMONTHS("lastclosetevtotalrevenue.lasttwelvemonths", Type.NUMBER),
    /** Levered Free Cash Flow. (Cash Flow Statement) */
    LEVEREDFREECASHFLOW_LASTTWELVEMONTHS("leveredfreecashflow.lasttwelvemonths", Type.NUMBER),
    /** Levered Free Cash Flow, 1 Yr. Growth %. (Cash Flow Statement) */
    LEVEREDFREECASHFLOW1YRGROWTH_LASTTWELVEMONTHS("leveredfreecashflow1yrgrowth.lasttwelvemonths", Type.NUMBER),
    /** LT Debt/Equity. (Share Statistics) */
    LTDEBTEQUITY_LASTTWELVEMONTHS("ltdebtequity.lasttwelvemonths", Type.NUMBER),
    /** Net Income Per Employee(FY). (Profitability ratios and Dividends) */
    NET_INCOME_PER_EMPLOYEE_ANNUAL("net_income_per_employee_annual", Type.NUMBER),
    /** Net Income Per Employee(FY). (Profitability ratios and Dividends) */
    NET_INCOME_PER_EMPLOYEE_ANNUAL_MARKET_CURRENCY("net_income_per_employee_annual_market_currency", Type.NUMBER),
    /** Net Debt / EBITDA. (Share Statistics) */
    NETDEBTEBITDA_LASTTWELVEMONTHS("netdebtebitda.lasttwelvemonths", Type.NUMBER),
    /** Net EPS - Basic. (Income) */
    NETEPSBASIC_LASTTWELVEMONTHS("netepsbasic.lasttwelvemonths", Type.NUMBER),
    /** Net EPS - Diluted. (Income) */
    NETEPSDILUTED_LASTTWELVEMONTHS("netepsdiluted.lasttwelvemonths", Type.NUMBER),
    /** Net Income, 1 Yr. Growth %. (Income) */
    NETINCOME1YRGROWTH_LASTTWELVEMONTHS("netincome1yrgrowth.lasttwelvemonths", Type.NUMBER),
    /** Net Income - (IS). (Income) */
    NETINCOMEIS_ANNUAL("netincomeis.annual", Type.NUMBER),
    /** Net Income - (IS). (Income) */
    NETINCOMEIS_LASTTWELVEMONTHS("netincomeis.lasttwelvemonths", Type.NUMBER),
    /** Net Income - (IS). (Income) */
    NETINCOMEISMARKETCURRENCY_ANNUAL("netincomeismarketcurrency.annual", Type.NUMBER),
    /** Net Income Margin %. (Income) */
    NETINCOMEMARGIN_LASTTWELVEMONTHS("netincomemargin.lasttwelvemonths", Type.NUMBER),
    /** New Listing Date. (Security Mapping) */
    NEW_LISTING_DATE("new_listing_date", Type.STRING),
    /** Operating Cash Flow to Current Liabilities. (Valuation Measures) */
    OPERATINGCASHFLOWTOCURRENTLIABILITIES_LASTTWELVEMONTHS("operatingcashflowtocurrentliabilities.lasttwelvemonths", Type.NUMBER),
    /** Operating Income. (Financial Highlights) */
    OPERATINGINCOME_LASTTWELVEMONTHS("operatingincome.lasttwelvemonths", Type.NUMBER),
    /** Page Views Weekly Growth. (User Insights) */
    PAGE_VIEW_GROWTH_WEEKLY("page_view_growth_weekly", Type.NUMBER),
    /** Percent of shares held by insiders. (Share Statistics) */
    PCTHELDINSIDER("pctheldinsider", Type.NUMBER),
    /** Percent of shares held by institutions. (Share Statistics) */
    PCTHELDINST("pctheldinst", Type.NUMBER),
    /** Peer Group. (ESG Scores) */
    PEER_GROUP("peer_group", Type.STRING),
    /** PEG Ratio (5 yr expected). (Valuation Measures) */
    PEGRATIO_5Y("pegratio_5y", Type.NUMBER),
    /** Trailing P/E. (Valuation Measures) */
    PERATIO_LASTTWELVEMONTHS("peratio.lasttwelvemonths", Type.NUMBER),
    /** Percent Change. (Share Statistics) */
    PERCENTCHANGE("percentchange", Type.NUMBER),
    /** Portfolio Held Count. (Portfolio Statistics) */
    PORTFOLIOHELDCOUNT("portfolioheldcount", Type.NUMBER),
    /** 52 Week High Date Time. (Signals) */
    PRICE_SIGNAL_FIFTY_TWO_WK_HIGH_DATETIME("price_signal_fifty_two_wk_high.datetime", Type.STRING),
    /** 52 Week Low Date Time. (Signals) */
    PRICE_SIGNAL_FIFTY_TWO_WK_LOW_DATETIME("price_signal_fifty_two_wk_low.datetime", Type.STRING),
    /** Price/Book. (Valuation Measures) */
    PRICEBOOKRATIO_QUARTERLY("pricebookratio.quarterly", Type.NUMBER),
    /** Quarterly Revenue Growth. (Income) */
    QUARTERLYREVENUEGROWTH_QUARTERLY("quarterlyrevenuegrowth.quarterly", Type.NUMBER),
    /** Quick Ratio. (Valuation Measures) */
    QUICKRATIO_LASTTWELVEMONTHS("quickratio.lasttwelvemonths", Type.NUMBER),
    /** Quotes Currency. (Profitability ratios and Dividends) */
    QUOTES_CURRENCY("quotesCurrency", Type.STRING),
    /** Region. (Share Statistics) */
    REGION("region", Type.STRING),
    /** RSI(14 Day). (Signals) */
    RELATIVE_STRENGTH_INDEX_14DAY("relative_strength_index_14day", Type.NUMBER),
    /** Relative Volume(1 Day). (Changes in Volume and Ownership) */
    RELATIVE_VOLUME_1DAY("relative_volume_1day", Type.NUMBER),
    /** Return on Assets. (Financial Highlights) */
    RETURNONASSETS_LASTTWELVEMONTHS("returnonassets.lasttwelvemonths", Type.NUMBER),
    /** Return On Equity %. (Financial Highlights) */
    RETURNONEQUITY_LASTTWELVEMONTHS("returnonequity.lasttwelvemonths", Type.NUMBER),
    /** Return on Total Capital. (Share Statistics) */
    RETURNONTOTALCAPITAL_LASTTWELVEMONTHS("returnontotalcapital.lasttwelvemonths", Type.NUMBER),
    /** Sector. (Sector &amp; Industry) */
    SECTOR("sector", Type.STRING),
    /** Security Lifecycle. (Security Lifecycle) */
    SECURITY_LIFECYCLE("security_lifecycle", Type.STRING),
    /** Short Interest. (Short Interest) */
    SHORT_INTEREST_VALUE("short_interest.value", Type.NUMBER),
    /** Short Interest % Change. (Short Interest) */
    SHORT_INTEREST_PERCENTAGE_CHANGE_VALUE("short_interest_percentage_change.value", Type.NUMBER),
    /** Short % of Float. (Short Interest) */
    SHORT_PERCENTAGE_OF_FLOAT_VALUE("short_percentage_of_float.value", Type.NUMBER),
    /** Short % of Shares Outstanding. (Short Interest) */
    SHORT_PERCENTAGE_OF_SHARES_OUTSTANDING_VALUE("short_percentage_of_shares_outstanding.value", Type.NUMBER),
    /** Shortcuts. (Ticker Alias) */
    SHORTCUTS("shortcuts", Type.STRING),
    /** Social Score. (ESG Scores) */
    SOCIAL_SCORE("social_score", Type.NUMBER),
    /** Symbol. (Popular Filters) */
    TICKER("ticker", Type.STRING),
    /** Total Revenues FY. (Income) */
    TOTAL_REVENUE_MARKET_CURRENCY_ANNUAL("total_revenue_market_currency.annual", Type.NUMBER),
    /** Total Revenue Per Employee(FY). (Profitability ratios and Dividends) */
    TOTAL_REVENUE_PER_EMPLOYEE_ANNUAL("total_revenue_per_employee_annual", Type.NUMBER),
    /** Total Revenue Per Employee(FY). (Profitability ratios and Dividends) */
    TOTAL_REVENUE_PER_EMPLOYEE_ANNUAL_MARKET_CURRENCY("total_revenue_per_employee_annual_market_currency", Type.NUMBER),
    /** Total Assets. (Share Statistics) */
    TOTALASSETS_LASTTWELVEMONTHS("totalassets.lasttwelvemonths", Type.NUMBER),
    /** Total Cash And Short Term Investments. (Share Statistics) */
    TOTALCASHANDSHORTTERMINVESTMENTS_LASTTWELVEMONTHS("totalcashandshortterminvestments.lasttwelvemonths", Type.NUMBER),
    /** Total Common Equity. (Share Statistics) */
    TOTALCOMMONEQUITY_LASTTWELVEMONTHS("totalcommonequity.lasttwelvemonths", Type.NUMBER),
    /** Total Common Shares Outstanding. (Share Statistics) */
    TOTALCOMMONSHARESOUTSTANDING_LASTTWELVEMONTHS("totalcommonsharesoutstanding.lasttwelvemonths", Type.NUMBER),
    /** Total Current Assets. (Share Statistics) */
    TOTALCURRENTASSETS_LASTTWELVEMONTHS("totalcurrentassets.lasttwelvemonths", Type.NUMBER),
    /** Total Current Liabilities. (Share Statistics) */
    TOTALCURRENTLIABILITIES_LASTTWELVEMONTHS("totalcurrentliabilities.lasttwelvemonths", Type.NUMBER),
    /** Total Debt. (Share Statistics) */
    TOTALDEBT_LASTTWELVEMONTHS("totaldebt.lasttwelvemonths", Type.NUMBER),
    /** Total Debt / EBITDA. (Share Statistics) */
    TOTALDEBTEBITDA_LASTTWELVEMONTHS("totaldebtebitda.lasttwelvemonths", Type.NUMBER),
    /** Total Debt/Equity. (Share Statistics) */
    TOTALDEBTEQUITY_LASTTWELVEMONTHS("totaldebtequity.lasttwelvemonths", Type.NUMBER),
    /** Total Equity. (Share Statistics) */
    TOTALEQUITY_LASTTWELVEMONTHS("totalequity.lasttwelvemonths", Type.NUMBER),
    /** Total Revenues FY. (Income) */
    TOTALREVENUES_ANNUAL("totalrevenues.annual", Type.NUMBER),
    /** Total Revenues. (Income) */
    TOTALREVENUES_LASTTWELVEMONTHS("totalrevenues.lasttwelvemonths", Type.NUMBER),
    /** Total Revenues, 1 Yr. Growth %. (Income) */
    TOTALREVENUES1YRGROWTH_LASTTWELVEMONTHS("totalrevenues1yrgrowth.lasttwelvemonths", Type.NUMBER),
    /** Total Shares Outstanding. (Share Statistics) */
    TOTALSHARESOUTSTANDING("totalsharesoutstanding", Type.NUMBER),
    /** Unlevered Free Cash Flow. (Cash Flow Statement) */
    UNLEVEREDFREECASHFLOW_LASTTWELVEMONTHS("unleveredfreecashflow.lasttwelvemonths", Type.NUMBER);

    private final String key;
    private final Type type;

    EquityScreenField(String key, Type type) {
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
