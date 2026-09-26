package io.github.dimazigel.yfinance.detail.rows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A single institutional or fund holder, from the {@code institutionOwnership} /
 * {@code fundOwnership} modules.
 */
public record InstitutionalHolder(
        Optional<LocalDate> reportDate,
        String organization,
        Optional<BigDecimal> pctHeld,
        Optional<Long> position,
        Optional<BigDecimal> value,
        Optional<BigDecimal> pctChange) {}
