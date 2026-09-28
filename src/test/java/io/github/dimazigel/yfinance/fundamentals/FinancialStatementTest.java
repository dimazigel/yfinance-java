package io.github.dimazigel.yfinance.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.LineItem;
import io.github.dimazigel.yfinance.enums.StatementType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinancialStatementTest {

    private static final LocalDate FY22 = LocalDate.parse("2022-09-30");
    private static final LocalDate FY23 = LocalDate.parse("2023-09-30");
    private static final LocalDate FY24 = LocalDate.parse("2024-09-30");

    /** Three periods; TotalRevenue at all three, NetIncome only in the middle one. */
    private static FinancialStatement income() {
        var revenue = new LinkedHashMap<LocalDate, BigDecimal>();
        revenue.put(FY24, new BigDecimal("391035000000"));   // deliberately out of order
        revenue.put(FY22, new BigDecimal("394328000000"));
        revenue.put(FY23, new BigDecimal("383285000000"));
        return new FinancialStatement(StatementType.INCOME, Frequency.ANNUAL, List.of(FY22, FY23, FY24),
                Map.of("TotalRevenue", revenue, "NetIncome", Map.of(FY23, new BigDecimal("96995000000"))));
    }

    @Test
    void latestPeriodIsTheLastAscendingPeriod() {
        assertThat(income().latestPeriod()).contains(FY24);
        assertThat(new FinancialStatement(StatementType.INCOME, Frequency.ANNUAL, List.of(), Map.of()).latestPeriod()).isEmpty();
    }

    @Test
    void latestReadsTheLineItemAtTheLatestPeriod() {
        assertThat(income().latest(LineItem.TOTAL_REVENUE)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("391035000000"));
        assertThat(income().latest(LineItem.NET_INCOME)).as("absent at the latest period, even though older periods have it").isEmpty();
        assertThat(income().latest(LineItem.GROSS_PROFIT)).as("line item not in the statement").isEmpty();
        assertThat(new FinancialStatement(StatementType.INCOME, Frequency.ANNUAL, List.of(), Map.of()).latest(LineItem.TOTAL_REVENUE)).isEmpty();
    }

    @Test
    void rowIsAscendingImmutableAndOmitsAbsentValues() {
        Map<LocalDate, BigDecimal> revenue = income().row(LineItem.TOTAL_REVENUE);
        assertThat(revenue.keySet()).containsExactly(FY22, FY23, FY24);
        assertThat(revenue.get(FY23)).isEqualByComparingTo("383285000000");
        assertThatThrownBy(() -> revenue.put(LocalDate.parse("2025-09-30"), BigDecimal.ONE)).isInstanceOf(UnsupportedOperationException.class);

        Map<LocalDate, BigDecimal> netIncome = income().row(LineItem.NET_INCOME);
        assertThat(netIncome).containsOnlyKeys(FY23);

        assertThat(income().row(LineItem.GROSS_PROFIT)).isEmpty();
    }

    @Test
    void typedAccessorsRejectALineItemOfAnotherStatement() {
        var income = income();
        String expected = "TOTAL_ASSETS belongs to BALANCE_SHEET, not INCOME";
        assertThatThrownBy(() -> income.value(LineItem.TOTAL_ASSETS, FY23)).isInstanceOf(IllegalArgumentException.class).hasMessage(expected);
        assertThatThrownBy(() -> income.latest(LineItem.TOTAL_ASSETS)).isInstanceOf(IllegalArgumentException.class).hasMessage(expected);
        assertThatThrownBy(() -> income.row(LineItem.TOTAL_ASSETS)).isInstanceOf(IllegalArgumentException.class).hasMessage(expected);
        assertThatThrownBy(() -> income.row(LineItem.OPERATING_CASH_FLOW)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OPERATING_CASH_FLOW belongs to CASH_FLOW, not INCOME");
        // the raw-key form stays lenient: a caller who spells the key takes responsibility
        assertThat(income.value("TotalAssets", FY23)).isEmpty();
    }
}
