package io.ziggy.yfinance.mapper;

import io.ziggy.yfinance.dto.YahooError;
import io.ziggy.yfinance.exception.YFDataException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import io.ziggy.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Small conversion helpers shared across DTO -> model mappers. */
final class MapperSupport {

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
            return null;
        }
    }

    static @Nullable Currency currency(@Nullable String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
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
            return null;
        }
    }

    /** The symbol Yahoo reported, or {@code fallback} when it is absent or blank. */
    static Symbol symbolOr(@Nullable String reported, Symbol fallback) {
        return reported == null || reported.isBlank() ? fallback : Symbol.of(reported);
    }

    /** Applies {@code accessor} to {@code source}, returning {@code null} when the source is null. */
    static <S, T extends @Nullable Object> @Nullable T from(@Nullable S source, Function<S, T> accessor) {
        return source == null ? null : accessor.apply(source);
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
