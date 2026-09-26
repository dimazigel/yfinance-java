package io.github.dimazigel.yfinance.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** A capital-gains distribution (typically for mutual funds). */
public record CapitalGain(Instant date, BigDecimal amount) {

    /** The distribution's trading date in {@code zone} (see {@link Dividend#localDate(ZoneId)}). */
    public LocalDate localDate(ZoneId zone) {
        return date.atZone(zone).toLocalDate();
    }
}
