package io.github.dimazigel.yfinance.detail.rows;

import java.time.Instant;
import java.util.Optional;

/** A single analyst rating change. */
public record UpgradeDowngrade(
        Optional<Instant> gradeDate,
        String firm,
        Optional<String> toGrade,
        Optional<String> fromGrade,
        Optional<String> action) {}
