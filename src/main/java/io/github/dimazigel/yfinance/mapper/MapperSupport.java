package io.github.dimazigel.yfinance.mapper;

import io.github.dimazigel.yfinance.dto.YahooError;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Small conversion helpers shared across DTO -> model mappers. */
final class MapperSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MapperSupport.class);

    private MapperSupport() {}

    static @Nullable Instant epochSecond(@Nullable Long seconds) {
        return seconds != null ? Instant.ofEpochSecond(seconds) : null;
    }

    static @Nullable URI uri(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            LOG.atDebug().log("Malformed URI \"{}\"; left null", value);
            return null;
        }
    }

    static @Nullable ZoneId zoneId(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(value);
        } catch (DateTimeException e) {
            LOG.atDebug().log("Unknown timezone \"{}\"; left null", value);
            return null;
        }
    }

    /** Parses an ISO date such as {@code 2023-09-30}, or {@code null} if absent or malformed. */
    static @Nullable LocalDate localDate(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            LOG.atDebug().log("Unparseable date \"{}\"; left null", value);
            return null;
        }
    }

    /** The symbol Yahoo reported, or {@code fallback} when it is absent or blank. */
    static Symbol symbolOr(@Nullable String reported, Symbol fallback) {
        return reported == null || reported.isBlank() ? fallback : Symbol.of(reported);
    }

    /** The {@link Interval} for a wire value such as {@code 1d}, or {@code null} if absent or unknown. */
    static @Nullable Interval interval(@Nullable String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        try {
            return Interval.fromWire(wire);
        } catch (IllegalArgumentException e) {
            LOG.atDebug().log("Unknown interval \"{}\"; left null", wire);
            return null;
        }
    }

    /** The known {@link Range}s among {@code wires}, in order; unknown values are dropped. */
    static List<Range> ranges(@Nullable List<String> wires) {
        if (wires == null) {
            return List.of();
        }
        var ranges = new ArrayList<Range>();
        for (String wire : wires) {
            try {
                ranges.add(Range.fromWire(wire));
            } catch (IllegalArgumentException e) {
                LOG.atDebug().log("Unknown range \"{}\"; ignored", wire); // Yahoo added one this version lacks
            }
        }
        return List.copyOf(ranges);
    }

    /**
     * Validates a standard Yahoo {@code {result:[...], error:{...}}} envelope and returns the first
     * result, raising {@link YFDataException} on a missing envelope, an error object, or no results.
     */
    static <T> T firstResult(@Nullable List<T> results, @Nullable Object error, String what, Object requested) {
        if (error != null) {
            throw new YFDataException("Yahoo error for " + requested + ": " + describe(error));
        }
        if (results == null || results.isEmpty()) {
            throw new YFDataException("No " + what + " returned for " + requested);
        }
        return results.getFirst();
    }

    /** Yahoo's own explanation (its {@code description}) verbatim, falling back to the code. */
    static String describe(Object error) {
        if (error instanceof YahooError e) {
            if (e.description() != null && !e.description().isBlank()) {
                return e.description();
            }
            if (e.code() != null) {
                return e.code();
            }
        }
        return error.toString();
    }
}
