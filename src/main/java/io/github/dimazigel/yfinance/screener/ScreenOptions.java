package io.github.dimazigel.yfinance.screener;

import java.util.Objects;
import java.util.Optional;

/**
 * Which slice of a screen to fetch, and in what order. Start from {@link #defaults()} and derive.
 *
 * @param offset how many matches to skip, at least 0
 * @param size how many instruments to return, 1 to {@value #MAX_SIZE} (Yahoo's own limit)
 * @param sort the order; empty leaves a predefined screen in its own order and sorts a custom query
 *     by symbol, descending, as Yahoo's own default does
 */
public record ScreenOptions(int offset, int size, Optional<Sort> sort) {

    /** The most instruments Yahoo returns per request. */
    public static final int MAX_SIZE = 250;

    private static final int DEFAULT_SIZE = 25;

    public ScreenOptions {
        Objects.requireNonNull(sort, "sort");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, was " + offset);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE + ", was " + size);
        }
    }

    /**
     * An order: a field and a direction.
     *
     * @param field the field to sort by
     * @param ascending smallest first when {@code true}, largest first otherwise
     */
    public record Sort(ScreenField field, boolean ascending) {}

    /** The first 25 matches, in the default order. */
    public static ScreenOptions defaults() {
        return new ScreenOptions(0, DEFAULT_SIZE, Optional.empty());
    }

    /** Returns a copy that skips {@code offset} matches. */
    public ScreenOptions withOffset(int offset) {
        return new ScreenOptions(offset, size, sort);
    }

    /** Returns a copy that returns {@code size} instruments, at most {@value #MAX_SIZE}. */
    public ScreenOptions withSize(int size) {
        return new ScreenOptions(offset, size, sort);
    }

    /** Returns a copy sorted by {@code field}. */
    public ScreenOptions sortedBy(ScreenField field, boolean ascending) {
        return new ScreenOptions(offset, size, Optional.of(new Sort(field, ascending)));
    }
}
