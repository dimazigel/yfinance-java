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
import java.util.TreeMap;

/**
 * A financial statement as a tabular structure: line items (rows) by reporting period (columns).
 * {@link #latest(LineItem)} reads the most recent period, {@link #row(LineItem)} one line item
 * across periods, {@link #value(LineItem, LocalDate)} one cell; the typed accessors reject a
 * {@link LineItem} of another statement.
 *
 * @param type      which statement this is
 * @param frequency the reporting frequency of its periods
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

    /**
     * Type-safe variant of {@link #value(String, LocalDate)}.
     *
     * @throws IllegalArgumentException when {@code lineItem} belongs to another statement, e.g.
     *     {@code TOTAL_ASSETS} asked of an income statement
     */
    public Optional<BigDecimal> value(LineItem lineItem, LocalDate period) {
        return value(checked(lineItem).key(), period);
    }

    /** The most recent reporting period — the last of the ascending {@link #periods()} — if any. */
    public Optional<LocalDate> latestPeriod() {
        return periods.isEmpty() ? Optional.empty() : Optional.of(periods.getLast());
    }

    /**
     * The line item's value at the {@linkplain #latestPeriod() latest period}; empty when there is
     * no period or Yahoo reported no value for that period (an older period may still have one —
     * see {@link #row(LineItem)}).
     *
     * @throws IllegalArgumentException when {@code lineItem} belongs to another statement
     */
    public Optional<BigDecimal> latest(LineItem lineItem) {
        LineItem item = checked(lineItem);
        return latestPeriod().flatMap(period -> value(item.key(), period));
    }

    /**
     * The line item's values by period, ascending; a period Yahoo reported no value for is absent.
     * Empty when the statement has no such line item. Unmodifiable.
     *
     * @throws IllegalArgumentException when {@code lineItem} belongs to another statement
     */
    public Map<LocalDate, BigDecimal> row(LineItem lineItem) {
        var values = lineItems.get(checked(lineItem).key());
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    private LineItem checked(LineItem lineItem) {
        if (lineItem.statement() != type) {
            throw new IllegalArgumentException(lineItem + " belongs to " + lineItem.statement() + ", not " + type);
        }
        return lineItem;
    }
}
