package io.github.dimazigel.yfinance.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/** A cash dividend distribution. */
public record Dividend(@Nullable Instant date, @Nullable BigDecimal amount) {

    /**
     * The dividend's trading date in {@code zone}. Prefer this over {@link #date()} when persisting,
     * since the raw UTC instant can fall on the previous/next calendar day in the exchange zone.
     */
    public @Nullable LocalDate localDate(ZoneId zone) {
        return date == null ? null : date.atZone(zone).toLocalDate();
    }
}
