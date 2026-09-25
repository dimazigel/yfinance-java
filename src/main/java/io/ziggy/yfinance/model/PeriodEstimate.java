package io.ziggy.yfinance.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * An analyst estimate (earnings or revenue) for a forward/trailing period such as {@code 0q}
 * or {@code +1y}.
 */
public record PeriodEstimate(
        @Nullable String period,
        @Nullable LocalDate endDate,
        @Nullable BigDecimal average,
        @Nullable BigDecimal low,
        @Nullable BigDecimal high,
        @Nullable Integer numberOfAnalysts,
        @Nullable BigDecimal yearAgoEps) {}
