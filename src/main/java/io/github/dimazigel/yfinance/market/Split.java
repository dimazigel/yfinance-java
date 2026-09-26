package io.github.dimazigel.yfinance.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** A stock split, e.g. 4-for-1 ({@code numerator=4, denominator=1}). */
public record Split(Instant date, BigDecimal numerator, BigDecimal denominator, String ratio) {

    /** The split's trading date in {@code zone} (see {@link Dividend#localDate(ZoneId)}). */
    public LocalDate localDate(ZoneId zone) {
        return date.atZone(zone).toLocalDate();
    }
}
