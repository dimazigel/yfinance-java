package io.github.dimazigel.yfinance.fundamentals;

import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.LineItem;
import io.github.dimazigel.yfinance.enums.StatementType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A financial statement as a tabular structure: line items (rows) by reporting period (columns).
 *
 * @param periods   the reporting periods, ascending
 * @param lineItems clean line-item name (prefix stripped, e.g. {@code TotalRevenue}) to its values
 *                  by period; a missing period entry means Yahoo reported no value
 */
public record FinancialStatement(
        StatementType type,
        Frequency frequency,
        List<LocalDate> periods,
        Map<String, Map<LocalDate, BigDecimal>> lineItems) {

    public FinancialStatement {
        periods = periods == null ? List.of() : List.copyOf(periods);
        if (lineItems == null || lineItems.isEmpty()) {
            lineItems = Map.of();
        } else {
            var copy = new LinkedHashMap<String, Map<LocalDate, BigDecimal>>();
            lineItems.forEach((name, values) -> {
                var valueCopy = values == null ? Map.<LocalDate, BigDecimal>of() : new LinkedHashMap<>(values);
                copy.put(name, Collections.unmodifiableMap(valueCopy));
            });
            lineItems = Collections.unmodifiableMap(copy);
        }
    }

    /** The value for a line item at a period; empty when Yahoo reported none. */
    public Optional<BigDecimal> value(String lineItem, LocalDate period) {
        var row = lineItems.get(lineItem);
        return row == null ? Optional.empty() : Optional.ofNullable(row.get(period));
    }

    /** Type-safe variant of {@link #value(String, LocalDate)}. */
    public Optional<BigDecimal> value(LineItem lineItem, LocalDate period) {
        return value(lineItem.key(), period);
    }
}
