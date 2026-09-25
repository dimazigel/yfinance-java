package io.github.dimazigel.yfinance.enums;

/** Candle interval accepted by the {@code /v8/finance/chart} endpoint. */
public enum Interval implements WireEnum {
    ONE_MINUTE("1m"),
    TWO_MINUTES("2m"),
    FIVE_MINUTES("5m"),
    FIFTEEN_MINUTES("15m"),
    THIRTY_MINUTES("30m"),
    SIXTY_MINUTES("60m"),
    NINETY_MINUTES("90m"),
    ONE_HOUR("1h"),
    ONE_DAY("1d"),
    FIVE_DAYS("5d"),
    ONE_WEEK("1wk"),
    ONE_MONTH("1mo"),
    THREE_MONTHS("3mo");

    private final String wireValue;

    Interval(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static Interval fromWire(String wire) {
        return WireEnum.fromWire(values(), wire, "interval");
    }
}
