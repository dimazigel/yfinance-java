package io.github.dimazigel.yfinance.detail.rows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** Actual-vs-estimate EPS for a past quarter, from the {@code earningsHistory} module. */
public record EarningsHistoryEntry(
        String period,
        Optional<Instant> quarter,
        Optional<BigDecimal> epsActual,
        Optional<BigDecimal> epsEstimate,
        Optional<BigDecimal> epsDifference,
        Optional<BigDecimal> surprisePercent) {}
