package io.github.dimazigel.yfinance.enums;

/** Reporting frequency for fundamentals, mapping to the timeseries {@code type} prefix. */
public enum Frequency implements WireEnum {
    ANNUAL("annual"),
    QUARTERLY("quarterly"),
    TRAILING("trailing");

    private final String prefix;

    Frequency(String prefix) {
        this.prefix = prefix;
    }

    /** The prefix prepended to each fundamental key (e.g. {@code annual} + {@code TotalRevenue}). */
    @Override
    public String wireValue() {
        return prefix;
    }
}
