package io.github.dimazigel.yfinance.enums;

/** Corporate-action event types requested via the chart endpoint's {@code events} param. */
public enum EventType implements WireEnum {
    DIVIDENDS("div"),
    SPLITS("splits"),
    CAPITAL_GAINS("capitalGains");

    private final String wireValue;

    EventType(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static EventType fromWire(String wire) {
        return WireEnum.fromWire(values(), wire, "event type");
    }
}
