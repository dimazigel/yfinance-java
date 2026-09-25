package io.github.dimazigel.yfinance.model;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Consolidated analyst price-target figures. */
public record AnalystPriceTarget(
        @Nullable BigDecimal current,
        @Nullable BigDecimal low,
        @Nullable BigDecimal high,
        @Nullable BigDecimal mean,
        @Nullable BigDecimal median,
        @Nullable Integer numberOfAnalysts) {}
