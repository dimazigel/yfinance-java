package io.github.dimazigel.yfinance.model;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Counts of analyst EPS estimate revisions for a period. */
public record EpsRevisionsPeriod(
        @Nullable String period,
        @Nullable LocalDate endDate,
        @Nullable Integer upLast7Days,
        @Nullable Integer upLast30Days,
        @Nullable Integer downLast30Days,
        @Nullable Integer downLast90Days) {}
