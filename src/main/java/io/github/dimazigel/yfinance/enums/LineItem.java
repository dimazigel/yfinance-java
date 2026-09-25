package io.github.dimazigel.yfinance.enums;

import java.util.List;
import java.util.stream.Stream;

/**
 * A typed financial-statement line item. Each constant carries the exact Yahoo timeseries key
 * (e.g. {@code TotalRevenue}) and the {@link StatementType} it belongs to, so consumers bind to
 * symbols instead of magic strings. Mirrors the most common keys from Python yfinance's const.py.
 */
public enum LineItem {
    // --- Income statement ---
    TOTAL_REVENUE("TotalRevenue", StatementType.INCOME),
    COST_OF_REVENUE("CostOfRevenue", StatementType.INCOME),
    GROSS_PROFIT("GrossProfit", StatementType.INCOME),
    OPERATING_EXPENSE("OperatingExpense", StatementType.INCOME),
    OPERATING_INCOME("OperatingIncome", StatementType.INCOME),
    NET_NON_OPERATING_INTEREST("NetNonOperatingInterestIncomeExpense", StatementType.INCOME),
    PRETAX_INCOME("PretaxIncome", StatementType.INCOME),
    TAX_PROVISION("TaxProvision", StatementType.INCOME),
    NET_INCOME("NetIncome", StatementType.INCOME),
    NET_INCOME_COMMON_STOCKHOLDERS("NetIncomeCommonStockholders", StatementType.INCOME),
    DILUTED_NI_AVAIL_TO_COM_STOCKHOLDERS("DilutedNIAvailtoComStockholders", StatementType.INCOME),
    BASIC_EPS("BasicEPS", StatementType.INCOME),
    DILUTED_EPS("DilutedEPS", StatementType.INCOME),
    BASIC_AVERAGE_SHARES("BasicAverageShares", StatementType.INCOME),
    DILUTED_AVERAGE_SHARES("DilutedAverageShares", StatementType.INCOME),
    EBIT("EBIT", StatementType.INCOME),
    EBITDA("EBITDA", StatementType.INCOME),
    INTEREST_EXPENSE("InterestExpense", StatementType.INCOME),
    RESEARCH_AND_DEVELOPMENT("ResearchAndDevelopment", StatementType.INCOME),
    SELLING_GENERAL_AND_ADMINISTRATION("SellingGeneralAndAdministration", StatementType.INCOME),

    // --- Balance sheet ---
    TOTAL_ASSETS("TotalAssets", StatementType.BALANCE_SHEET),
    CURRENT_ASSETS("CurrentAssets", StatementType.BALANCE_SHEET),
    CASH_AND_CASH_EQUIVALENTS("CashAndCashEquivalents", StatementType.BALANCE_SHEET),
    CASH_CASH_EQUIVALENTS_AND_SHORT_TERM_INVESTMENTS(
            "CashCashEquivalentsAndShortTermInvestments", StatementType.BALANCE_SHEET),
    RECEIVABLES("Receivables", StatementType.BALANCE_SHEET),
    INVENTORY("Inventory", StatementType.BALANCE_SHEET),
    NET_PPE("NetPPE", StatementType.BALANCE_SHEET),
    GOODWILL("Goodwill", StatementType.BALANCE_SHEET),
    TOTAL_LIABILITIES_NET_MINORITY_INTEREST(
            "TotalLiabilitiesNetMinorityInterest", StatementType.BALANCE_SHEET),
    CURRENT_LIABILITIES("CurrentLiabilities", StatementType.BALANCE_SHEET),
    ACCOUNTS_PAYABLE("AccountsPayable", StatementType.BALANCE_SHEET),
    CURRENT_DEBT("CurrentDebt", StatementType.BALANCE_SHEET),
    LONG_TERM_DEBT("LongTermDebt", StatementType.BALANCE_SHEET),
    TOTAL_DEBT("TotalDebt", StatementType.BALANCE_SHEET),
    STOCKHOLDERS_EQUITY("StockholdersEquity", StatementType.BALANCE_SHEET),
    RETAINED_EARNINGS("RetainedEarnings", StatementType.BALANCE_SHEET),
    COMMON_STOCK("CommonStock", StatementType.BALANCE_SHEET),
    TREASURY_SHARES_NUMBER("TreasurySharesNumber", StatementType.BALANCE_SHEET),
    SHARE_ISSUED("ShareIssued", StatementType.BALANCE_SHEET),
    WORKING_CAPITAL("WorkingCapital", StatementType.BALANCE_SHEET),
    FIXED_MATURITY_INVESTMENTS("FixedMaturityInvestments", StatementType.BALANCE_SHEET),
    EQUITY_INVESTMENTS("EquityInvestments", StatementType.BALANCE_SHEET),
    NET_LOAN("NetLoan", StatementType.BALANCE_SHEET),
    DEFERRED_ASSETS("DeferredAssets", StatementType.BALANCE_SHEET),

    // --- Cash flow ---
    OPERATING_CASH_FLOW("OperatingCashFlow", StatementType.CASH_FLOW),
    INVESTING_CASH_FLOW("InvestingCashFlow", StatementType.CASH_FLOW),
    FINANCING_CASH_FLOW("FinancingCashFlow", StatementType.CASH_FLOW),
    FREE_CASH_FLOW("FreeCashFlow", StatementType.CASH_FLOW),
    CAPITAL_EXPENDITURE("CapitalExpenditure", StatementType.CASH_FLOW),
    END_CASH_POSITION("EndCashPosition", StatementType.CASH_FLOW),
    BEGINNING_CASH_POSITION("BeginningCashPosition", StatementType.CASH_FLOW),
    CHANGES_IN_CASH("ChangesInCash", StatementType.CASH_FLOW),
    DEPRECIATION_AND_AMORTIZATION("DepreciationAndAmortization", StatementType.CASH_FLOW),
    STOCK_BASED_COMPENSATION("StockBasedCompensation", StatementType.CASH_FLOW),
    NET_INCOME_FROM_CONTINUING_OPERATIONS("NetIncomeFromContinuingOperations", StatementType.CASH_FLOW),
    REPURCHASE_OF_CAPITAL_STOCK("RepurchaseOfCapitalStock", StatementType.CASH_FLOW),
    ISSUANCE_OF_DEBT("IssuanceOfDebt", StatementType.CASH_FLOW),
    REPAYMENT_OF_DEBT("RepaymentOfDebt", StatementType.CASH_FLOW),
    CASH_DIVIDENDS_PAID("CashDividendsPaid", StatementType.CASH_FLOW),
    CHANGE_IN_WORKING_CAPITAL("ChangeInWorkingCapital", StatementType.CASH_FLOW),
    NET_OTHER_FINANCING_CHARGES("NetOtherFinancingCharges", StatementType.CASH_FLOW),
    INTEREST_PAID_SUPPLEMENTAL_DATA("InterestPaidSupplementalData", StatementType.CASH_FLOW),
    INCOME_TAX_PAID_SUPPLEMENTAL_DATA("IncomeTaxPaidSupplementalData", StatementType.CASH_FLOW),
    EFFECT_OF_EXCHANGE_RATE_CHANGES("EffectOfExchangeRateChanges", StatementType.CASH_FLOW);

    private final String key;
    private final StatementType statement;

    LineItem(String key, StatementType statement) {
        this.key = key;
        this.statement = statement;
    }

    /** The exact Yahoo timeseries key (without the frequency prefix), e.g. {@code TotalRevenue}. */
    public String key() {
        return key;
    }

    public StatementType statement() {
        return statement;
    }

    /** All line items belonging to the given statement, in declaration order. */
    public static List<LineItem> forStatement(StatementType statement) {
        return Stream.of(values()).filter(li -> li.statement == statement).toList();
    }
}
