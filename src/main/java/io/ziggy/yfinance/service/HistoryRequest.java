package io.ziggy.yfinance.service;

import io.ziggy.yfinance.enums.EventType;
import io.ziggy.yfinance.enums.Interval;
import io.ziggy.yfinance.enums.Range;
import io.ziggy.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Parameters for a price-history query.
 *
 * <p>{@code range} and the {@code (start, end)} period are mutually exclusive; if a period is
 * supplied it takes precedence over {@code range}.
 */
public record HistoryRequest(
        Symbol symbol,
        Interval interval,
        @Nullable Range range,
        @Nullable Instant start,
        @Nullable Instant end,
        boolean includePrePost,
        Set<EventType> events) {

    public HistoryRequest {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(interval, "interval");
        events = events == null || events.isEmpty()
                ? EnumSet.noneOf(EventType.class)
                : EnumSet.copyOf(events);
        if (range == null && start == null) {
            throw new IllegalArgumentException("Either range or a (start, end) period must be set");
        }
        if (start != null && end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("end (" + end + ") must be after start (" + start + ")");
        }
    }

    public boolean hasPeriod() {
        return start != null;
    }

    public static Builder builder(Symbol symbol) {
        return new Builder(symbol);
    }

    /** Fluent builder with sensible defaults (1d interval, all corporate-action events). */
    public static final class Builder {
        private final Symbol symbol;
        private Interval interval = Interval.ONE_DAY;
        private @Nullable Range range;
        private @Nullable Instant start;
        private @Nullable Instant end;
        private boolean includePrePost = false;
        private Set<EventType> events = EnumSet.allOf(EventType.class);

        private Builder(Symbol symbol) {
            this.symbol = Objects.requireNonNull(symbol, "symbol");
        }

        public Builder interval(Interval interval) {
            this.interval = interval;
            return this;
        }

        public Builder range(Range range) {
            this.range = range;
            this.start = null;
            this.end = null;
            return this;
        }

        public Builder period(Instant start, @Nullable Instant end) {
            this.start = start;
            this.end = end;
            this.range = null;
            return this;
        }

        public Builder includePrePost(boolean includePrePost) {
            this.includePrePost = includePrePost;
            return this;
        }

        public Builder events(Set<EventType> events) {
            this.events = events;
            return this;
        }

        public HistoryRequest build() {
            return new HistoryRequest(symbol, interval, range, start, end, includePrePost, events);
        }
    }
}
