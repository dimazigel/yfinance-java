package io.github.dimazigel.yfinance.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Consensus growth estimate for a period (e.g. {@code 0q}, {@code +1y}). */
public record GrowthEstimate(@Nullable String period, @Nullable LocalDate endDate, @Nullable BigDecimal growth) {}
