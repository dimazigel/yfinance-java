package io.github.dimazigel.yfinance.enums;

/** Pre-defined look-back range accepted by the {@code /v8/finance/chart} endpoint. */
public enum Range implements WireEnum {
    ONE_DAY("1d"),
    FIVE_DAYS("5d"),
    ONE_MONTH("1mo"),
    THREE_MONTHS("3mo"),
    SIX_MONTHS("6mo"),
    ONE_YEAR("1y"),
    TWO_YEARS("2y"),
    FIVE_YEARS("5y"),
    TEN_YEARS("10y"),
    YEAR_TO_DATE("ytd"),
    MAX("max");

    private final String wireValue;

    Range(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static Range fromWire(String wire) {
        return WireEnum.fromWire(values(), wire, "range");
    }
}
