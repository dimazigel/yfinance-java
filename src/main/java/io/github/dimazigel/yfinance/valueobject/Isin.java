package io.github.dimazigel.yfinance.valueobject;

import java.util.Locale;
import java.util.Objects;

/**
 * An International Securities Identification Number (ISO 6166), e.g. {@code US0378331005} for
 * Apple: a two-letter country code, nine letters or digits, and a check digit.
 *
 * <p>The value is trimmed and upper-cased, and its shape and check digit are verified, so a typo
 * fails here instead of quietly matching nothing.
 */
public record Isin(String value) {

    private static final int LENGTH = 12;

    public Isin {
        Objects.requireNonNull(value, "value");
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]")) {
            throw new IllegalArgumentException("Not an ISIN: \"" + value + "\" (expected two letters, nine letters or digits,"
                    + " and a check digit: " + LENGTH + " characters)");
        }
        if (!hasValidCheckDigit(normalized)) {
            throw new IllegalArgumentException("Not an ISIN: \"" + normalized + "\" has a wrong check digit");
        }
        value = normalized;
    }

    public static Isin of(String value) {
        return new Isin(value);
    }

    /** The two-letter prefix: usually the country of the issuer, e.g. {@code US}. */
    public String countryCode() {
        return value.substring(0, 2);
    }

    @Override
    public String toString() {
        return value;
    }

    /** Luhn's algorithm over the digits obtained by writing each letter as its value (A = 10 … Z = 35). */
    private static boolean hasValidCheckDigit(String isin) {
        var digits = new StringBuilder(2 * LENGTH);
        for (int i = 0; i < isin.length(); i++) {
            char c = isin.charAt(i);   // validated above: 0-9 or A-Z
            digits.append(c <= '9' ? c - '0' : c - 'A' + 10);
        }
        int sum = 0;
        boolean doubled = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubled) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubled = !doubled;
        }
        return sum % 10 == 0;
    }
}
