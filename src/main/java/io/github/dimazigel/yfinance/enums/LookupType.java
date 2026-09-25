package io.github.dimazigel.yfinance.enums;

/** Instrument type filter for the {@code /v1/finance/lookup} endpoint. */
public enum LookupType implements WireEnum {
    ALL("all"),
    EQUITY("equity"),
    MUTUAL_FUND("mutualfund"),
    ETF("etf"),
    INDEX("index"),
    FUTURE("future"),
    CURRENCY("currency"),
    CRYPTOCURRENCY("cryptocurrency");

    private final String wireValue;

    LookupType(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static LookupType fromWire(String wire) {
        return WireEnum.fromWire(values(), wire, "lookup type");
    }
}
