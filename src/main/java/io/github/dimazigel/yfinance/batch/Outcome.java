package io.github.dimazigel.yfinance.batch;

import io.github.dimazigel.yfinance.exception.YFMissingDataException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.Objects;
import java.util.Optional;

/**
 * The result for one symbol in a batch: a value, a non-retryable skip with a reason, or a
 * retryable failure. Sealed, so a {@code switch} over it needs no default.
 */
public sealed interface Outcome<T> permits Outcome.Ok, Outcome.Skipped, Outcome.Failed {

    Symbol symbol();

    /** An Optional containing the value if this is {@link Ok}, empty otherwise. */
    default Optional<T> optional() {
        return this instanceof Ok<T> ok ? Optional.of(ok.value()) : Optional.empty();
    }

    /**
     * The value, or the failure rethrown, or — for a skip — a {@link YFMissingDataException} whose
     * {@code field()} is the skip detail and {@code subject()} the symbol.
     */
    T orElseThrow();

    static <T> Outcome<T> ok(Symbol symbol, T value) {
        return new Ok<>(symbol, value);
    }

    static <T> Outcome<T> skipped(Symbol symbol, SkipReason reason, String detail) {
        return new Skipped<>(symbol, reason, detail);
    }

    static <T> Outcome<T> failed(Symbol symbol, YFinanceException error) {
        return new Failed<>(symbol, error);
    }

    /** A symbol whose fetch produced a value. */
    record Ok<T>(Symbol symbol, T value) implements Outcome<T> {
        public Ok {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(value, "value");
        }

        @Override
        public T orElseThrow() {
            return value;
        }
    }

    /**
     * A symbol the library chose not to answer for; {@link #reason()} says why (never a transport
     * failure) and {@link #detail()} what exactly was missing or wrong. {@link #orElseThrow()} turns
     * it into a {@link YFMissingDataException} with {@code detail} as the field and the symbol as
     * the subject — what {@code Ticker.instrument()} and {@code Ticker.detail(...)} throw.
     */
    record Skipped<T>(Symbol symbol, SkipReason reason, String detail) implements Outcome<T> {
        public Skipped {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }

        @Override
        public T orElseThrow() {
            throw new YFMissingDataException(detail, symbol.value(), symbol + " skipped: " + reason + " (" + detail + ")");
        }
    }

    /** A symbol whose fetch failed with an exception; the batch carried on without it. */
    record Failed<T>(Symbol symbol, YFinanceException error) implements Outcome<T> {
        public Failed {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(error, "error");
        }

        @Override
        public T orElseThrow() {
            throw error;
        }
    }
}
