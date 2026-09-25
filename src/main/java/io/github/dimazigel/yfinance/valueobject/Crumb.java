package io.github.dimazigel.yfinance.valueobject;

import java.util.Objects;

/**
 * The anti-CSRF "crumb" token Yahoo Finance requires on authenticated endpoints.
 */
public record Crumb(String value) {

    public Crumb {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Crumb must not be blank");
        }
    }

    public static Crumb of(String value) {
        return new Crumb(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
