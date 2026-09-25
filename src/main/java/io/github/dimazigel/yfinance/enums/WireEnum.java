package io.github.dimazigel.yfinance.enums;

/**
 * An enum whose constants map to a stable string used on the Yahoo Finance wire.
 */
public interface WireEnum {

    /** The exact string Yahoo Finance expects/returns for this constant. */
    String wireValue();

    /**
     * Resolves the constant whose {@link #wireValue()} equals {@code wire}.
     *
     * @throws IllegalArgumentException if no constant matches
     */
    static <E extends Enum<E> & WireEnum> E fromWire(E[] values, String wire, String label) {
        for (E value : values) {
            if (value.wireValue().equals(wire)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown " + label + ": " + wire);
    }
}
