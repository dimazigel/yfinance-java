package io.github.dimazigel.yfinance.market;

import io.github.dimazigel.yfinance.enums.EventType;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Parameters for a price-history query, independent of any symbol — the type {@code
 * Ticker.history(HistoryQuery)}, {@code Tickers.histories(HistoryQuery)} and {@code
 * YFinance.histories(java.util.Collection, HistoryQuery)} take.
 *
 * <p>{@code range} and the {@code (start, end)} period are mutually exclusive: exactly one of
 * {@code range} or {@code start} is present. An absent {@code end} with a present {@code start}
 * means an open-ended window, which resolves to "now" at request time.
 */
public record HistoryQuery(
        Interval interval,
        Optional<Range> range,
        Optional<Instant> start,
        Optional<Instant> end,
        boolean includePrePost,
        Set<EventType> events) {

    /**
     * Canonical constructor. Enforces that exactly one of {@code range}/{@code start} is present,
     * that {@code end} is only set together with {@code start} and is after it, and copies {@code
     * events} into an immutable set.
     */
    public HistoryQuery {
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(events, "events");
        if (range.isPresent() == start.isPresent()) {
            throw new IllegalArgumentException("Either range or a (start, end) period must be set, not both");
        }
        if (end.isPresent() && start.isEmpty()) {
            throw new IllegalArgumentException("end may only be set together with start");
        }
        if (start.isPresent() && end.isPresent() && !end.get().isAfter(start.get())) {
            throw new IllegalArgumentException("end (" + end.get() + ") must be after start (" + start.get() + ")");
        }
        events = Collections.unmodifiableSet(
                events.isEmpty() ? EnumSet.noneOf(EventType.class) : EnumSet.copyOf(events));
    }

    /** Whether this query is an explicit {@code (start, end)} period rather than a {@link Range}. */
    public boolean hasPeriod() {
        return start.isPresent();
    }

    /**
     * A builder seeded with {@code interval}; call {@link Builder#range(Range)} or one of the
     * {@link Builder#period} overloads before {@link Builder#build()}.
     */
    public static Builder of(Interval interval) {
        return new Builder(interval);
    }

    /** {@code range} over {@code interval}, with the builder's other defaults; see {@link Builder}. */
    public static HistoryQuery range(Range range, Interval interval) {
        return of(interval).range(range).build();
    }

    /** The {@code [start, end)} period over {@code interval}, with the builder's other defaults. */
    public static HistoryQuery period(Instant start, Instant end, Interval interval) {
        return of(interval).period(start, end).build();
    }

    /**
     * Fluent builder with sensible defaults: {@code includePrePost=false}, every {@link
     * EventType}. Exactly one of {@link #range(Range)} or {@link #period} must be called before
     * {@link #build()}.
     */
    public static final class Builder {
        private final Interval interval;
        private @Nullable Range range;
        private @Nullable Instant start;
        private @Nullable Instant end;
        private boolean includePrePost = false;
        private Set<EventType> events = EnumSet.allOf(EventType.class);

        private Builder(Interval interval) {
            this.interval = Objects.requireNonNull(interval, "interval");
        }

        /** Look back over {@code range}, clearing any period previously set. */
        public Builder range(Range range) {
            this.range = Objects.requireNonNull(range, "range");
            this.start = null;
            this.end = null;
            return this;
        }

        /** The explicit {@code [start, end)} window, clearing any range previously set. */
        public Builder period(Instant start, Instant end) {
            this.start = Objects.requireNonNull(start, "start");
            this.end = Objects.requireNonNull(end, "end");
            this.range = null;
            return this;
        }

        /**
         * An open-ended window from {@code start}: resolves to "now" at request time, clearing any
         * range previously set.
         */
        public Builder period(Instant start) {
            this.start = Objects.requireNonNull(start, "start");
            this.end = null;
            this.range = null;
            return this;
        }

        /** Whether to include pre/post-market bars; default {@code false}. */
        public Builder includePrePost(boolean includePrePost) {
            this.includePrePost = includePrePost;
            return this;
        }

        /** The corporate-action events to include; default every {@link EventType}. */
        public Builder events(Set<EventType> events) {
            this.events = Objects.requireNonNull(events, "events");
            return this;
        }

        /** {@link #events(Set)} from varargs; no arguments means no events. */
        public Builder events(EventType... events) {
            return events(events.length == 0 ? EnumSet.noneOf(EventType.class) : EnumSet.copyOf(Arrays.asList(events)));
        }

        /**
         * Builds the query.
         *
         * @throws IllegalArgumentException if neither {@link #range(Range)} nor {@link #period} was
         *     called, or if the period's end is not after its start
         */
        public HistoryQuery build() {
            return new HistoryQuery(
                    interval, Optional.ofNullable(range), Optional.ofNullable(start),
                    Optional.ofNullable(end), includePrePost, events);
        }
    }
}
