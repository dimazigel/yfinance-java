package io.github.dimazigel.yfinance.instrument;

import org.jspecify.annotations.Nullable;

/** Yahoo's session state; unknown values map to {@link #OTHER} rather than failing. */
public enum MarketState {
    PRE, REGULAR, POST, CLOSED, PREPRE, POSTPOST, OTHER;

    public static MarketState fromWire(@Nullable String wire) {
        if (wire == null) {
            return OTHER;
        }
        return switch (wire) {
            case "PRE" -> PRE;
            case "REGULAR" -> REGULAR;
            case "POST" -> POST;
            case "CLOSED" -> CLOSED;
            case "PREPRE" -> PREPRE;
            case "POSTPOST" -> POSTPOST;
            default -> OTHER;
        };
    }
}
