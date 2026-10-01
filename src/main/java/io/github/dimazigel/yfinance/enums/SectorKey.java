package io.github.dimazigel.yfinance.enums;

import java.util.Optional;

/** Yahoo Finance's eleven sectors; the wire value is the key in sector URLs and in {@code sectorKey} fields. */
public enum SectorKey implements WireEnum {
    BASIC_MATERIALS("basic-materials"),
    COMMUNICATION_SERVICES("communication-services"),
    CONSUMER_CYCLICAL("consumer-cyclical"),
    CONSUMER_DEFENSIVE("consumer-defensive"),
    ENERGY("energy"),
    FINANCIAL_SERVICES("financial-services"),
    HEALTHCARE("healthcare"),
    INDUSTRIALS("industrials"),
    REAL_ESTATE("real-estate"),
    TECHNOLOGY("technology"),
    UTILITIES("utilities");

    private final String wireValue;

    SectorKey(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    /**
     * The sector with this key, or empty for a key this library does not know — Yahoo's keys are
     * data, so a sector added on its side must not break the caller.
     */
    public static Optional<SectorKey> ofKey(String key) {
        for (SectorKey sector : values()) {
            if (sector.wireValue.equals(key)) {
                return Optional.of(sector);
            }
        }
        return Optional.empty();
    }
}
