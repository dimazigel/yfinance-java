package io.ziggy.yfinance.model;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Actual-vs-estimate EPS for a past quarter, from the {@code earningsHistory} module. */
public record EarningsHistoryEntry(
        @Nullable String period,
        @Nullable Instant quarter,
        @Nullable BigDecimal epsActual,
        @Nullable BigDecimal epsEstimate,
        @Nullable BigDecimal epsDifference,
        @Nullable BigDecimal surprisePercent) {}
