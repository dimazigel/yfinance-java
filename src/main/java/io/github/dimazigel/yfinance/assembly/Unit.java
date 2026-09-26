package io.github.dimazigel.yfinance.assembly;

/**
 * How a wire value is encoded and how it must be converted for typed access. {@link
 * Resolved}'s typed getters apply the effective unit (the winning path's override, else the
 * field's own {@link FieldSpec#unit()}) before returning a value.
 */
public enum Unit {
    /** No conversion: the wire value is already in its final representation. */
    RAW,
    /** A percent on the wire (e.g. {@code 20.8}), stored and returned as a fraction ({@code 0.208}). */
    PERCENT,
    /** Epoch seconds, converted to {@link java.time.Instant}. */
    EPOCH_SECONDS,
    /** Epoch milliseconds, converted to {@link java.time.Instant}. */
    EPOCH_MILLIS,
    /** An epoch (seconds) that names a calendar day, converted to {@link java.time.LocalDate} (UTC). */
    EPOCH_DATE,
    /** An ISO-8601 date string ({@code yyyy-MM-dd}), converted to {@link java.time.LocalDate}. */
    ISO_DATE
}
