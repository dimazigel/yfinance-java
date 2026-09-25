package io.github.dimazigel.yfinance.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** How the consensus EPS estimate for a period has drifted over the trailing 90 days. */
public record EpsTrendPeriod(
        @Nullable String period,
        @Nullable LocalDate endDate,
        @Nullable BigDecimal current,
        @Nullable BigDecimal sevenDaysAgo,
        @Nullable BigDecimal thirtyDaysAgo,
        @Nullable BigDecimal sixtyDaysAgo,
        @Nullable BigDecimal ninetyDaysAgo) {}
