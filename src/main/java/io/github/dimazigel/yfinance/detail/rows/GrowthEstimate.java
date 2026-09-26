package io.github.dimazigel.yfinance.detail.rows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Consensus growth estimate for a period (e.g. {@code 0q}, {@code +1y}). */
public record GrowthEstimate(String period, Optional<LocalDate> endDate, Optional<BigDecimal> growth) {}
