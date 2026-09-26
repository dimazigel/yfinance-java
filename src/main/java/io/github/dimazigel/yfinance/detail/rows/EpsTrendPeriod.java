package io.github.dimazigel.yfinance.detail.rows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** How the consensus EPS estimate for a period has drifted over the trailing 90 days. */
public record EpsTrendPeriod(
        String period,
        Optional<LocalDate> endDate,
        Optional<BigDecimal> current,
        Optional<BigDecimal> sevenDaysAgo,
        Optional<BigDecimal> thirtyDaysAgo,
        Optional<BigDecimal> sixtyDaysAgo,
        Optional<BigDecimal> ninetyDaysAgo) {}
