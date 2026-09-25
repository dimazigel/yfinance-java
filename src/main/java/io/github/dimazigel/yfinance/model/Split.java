package io.github.dimazigel.yfinance.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/** A stock split, e.g. 4-for-1 ({@code numerator=4, denominator=1}). */
public record Split(
        @Nullable Instant date, @Nullable BigDecimal numerator, @Nullable BigDecimal denominator, @Nullable String ratio) {

    /** The split's trading date in {@code zone} (see {@link Dividend#localDate(ZoneId)}). */
    public @Nullable LocalDate localDate(ZoneId zone) {
        return date == null ? null : date.atZone(zone).toLocalDate();
    }
}
