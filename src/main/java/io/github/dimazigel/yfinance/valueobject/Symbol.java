package io.github.dimazigel.yfinance.valueobject;

import java.util.Locale;
import java.util.Objects;

/**
 * A Yahoo Finance ticker symbol (e.g. {@code AAPL}, {@code ^GSPC}, {@code BRK-B}, {@code ES=F}).
 *
 * <p>Symbols are trimmed and upper-cased, but Yahoo's special characters
 * ({@code ^ - = . }) are preserved.
 */
public record Symbol(String value) {

    public Symbol {
        Objects.requireNonNull(value, "value");
        var normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        value = normalized;
    }

    public static Symbol of(String value) {
        return new Symbol(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
