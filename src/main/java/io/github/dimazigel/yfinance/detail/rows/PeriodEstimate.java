package io.github.dimazigel.yfinance.detail.rows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * An analyst estimate (earnings or revenue) for a forward/trailing period such as {@code 0q} or
 * {@code +1y}.
 */
public record PeriodEstimate(
        String period,
        Optional<LocalDate> endDate,
        Optional<BigDecimal> average,
        Optional<BigDecimal> low,
        Optional<BigDecimal> high,
        Optional<Integer> numberOfAnalysts,
        Optional<BigDecimal> yearAgo) {}
