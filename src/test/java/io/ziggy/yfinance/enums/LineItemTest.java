package io.ziggy.yfinance.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LineItemTest {

    @Test
    void carriesYahooKeyAndStatement() {
        assertThat(LineItem.TOTAL_REVENUE.key()).isEqualTo("TotalRevenue");
        assertThat(LineItem.TOTAL_REVENUE.statement()).isEqualTo(StatementType.INCOME);
        assertThat(LineItem.TOTAL_ASSETS.statement()).isEqualTo(StatementType.BALANCE_SHEET);
        assertThat(LineItem.FREE_CASH_FLOW.statement()).isEqualTo(StatementType.CASH_FLOW);
    }

    @Test
    void groupsByStatement() {
        assertThat(LineItem.forStatement(StatementType.INCOME))
                .contains(LineItem.TOTAL_REVENUE, LineItem.NET_INCOME)
                .allMatch(li -> li.statement() == StatementType.INCOME);
        assertThat(LineItem.forStatement(StatementType.CASH_FLOW)).contains(LineItem.FREE_CASH_FLOW);
    }

    @Test
    void balanceSheetIncludesFinancialSectorKeysAddedUpstream() {
        // yfinance 1.6.0 (const.py): keys reported by insurers/banks.
        assertThat(LineItem.forStatement(StatementType.BALANCE_SHEET))
                .extracting(LineItem::key)
                .contains("FixedMaturityInvestments", "EquityInvestments", "NetLoan", "DeferredAssets");
    }
}
