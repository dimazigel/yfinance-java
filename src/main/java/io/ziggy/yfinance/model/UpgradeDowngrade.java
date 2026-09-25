package io.ziggy.yfinance.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A single analyst rating change. */
public record UpgradeDowngrade(
        @Nullable Instant gradeDate,
        @Nullable String firm,
        @Nullable String toGrade,
        @Nullable String fromGrade,
        @Nullable String action) {}
