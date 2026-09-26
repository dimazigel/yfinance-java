package io.github.dimazigel.yfinance.instrument;

/** Yahoo's session state; unknown values map to {@link #OTHER} rather than failing. */
public enum MarketState {
    PRE, REGULAR, POST, CLOSED, PREPRE, POSTPOST, OTHER;

    public static MarketState fromWire(String wire) {
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
