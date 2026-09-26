package io.github.dimazigel.yfinance.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** A cash dividend distribution. */
public record Dividend(Instant date, BigDecimal amount) {

    /**
     * The dividend's trading date in {@code zone}. Prefer this over {@link #date()} when persisting,
     * since the raw UTC instant can fall on the previous/next calendar day in the exchange zone.
     */
    public LocalDate localDate(ZoneId zone) {
        return date.atZone(zone).toLocalDate();
    }
}
