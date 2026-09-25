package io.ziggy.yfinance.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/** A capital-gains distribution (typically for mutual funds). */
public record CapitalGain(@Nullable Instant date, @Nullable BigDecimal amount) {

    /** The distribution's trading date in {@code zone} (see {@link Dividend#localDate(ZoneId)}). */
    public @Nullable LocalDate localDate(ZoneId zone) {
        return date == null ? null : date.atZone(zone).toLocalDate();
    }
}
