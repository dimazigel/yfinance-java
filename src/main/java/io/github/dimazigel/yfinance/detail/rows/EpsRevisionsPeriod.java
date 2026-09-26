package io.github.dimazigel.yfinance.detail.rows;

import java.time.LocalDate;
import java.util.Optional;

/** Counts of analyst EPS estimate revisions for a period. */
public record EpsRevisionsPeriod(
        String period,
        Optional<LocalDate> endDate,
        Optional<Integer> upLast7Days,
        Optional<Integer> upLast30Days,
        Optional<Integer> downLast30Days,
        Optional<Integer> downLast90Days) {}
