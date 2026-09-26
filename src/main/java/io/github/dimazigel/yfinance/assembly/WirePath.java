package io.github.dimazigel.yfinance.assembly;

import java.util.Objects;
import java.util.Optional;

/**
 * Where a field lives on the wire: {@code v7:marketCap} or {@code qs:price.marketCap}. A path may
 * override the field's unit ({@code v7:dividendYield|PERCENT}) because Yahoo serves the same figure
 * as a percent on one endpoint and a fraction on the other.
 */
public record WirePath(Source source, String path, Optional<Unit> unit) {

    public WirePath {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(unit, "unit");
    }

    public static WirePath parse(String spec) {
        int colon = spec.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Wire path must be 'v7:<key>' or 'qs:<module.key>': " + spec);
        }
        String prefix = spec.substring(0, colon);
        String rest = spec.substring(colon + 1);
        Optional<Unit> unit = Optional.empty();
        int bar = rest.indexOf('|');
        if (bar >= 0) {
            try {
                unit = Optional.of(Unit.valueOf(rest.substring(bar + 1)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown unit in wire path " + spec, e);
            }
            rest = rest.substring(0, bar);
        }
        return switch (prefix) {
            case "v7" -> new WirePath(Source.V7, rest, unit);
            case "qs" -> new WirePath(Source.QUOTE_SUMMARY, rest, unit);
            default -> throw new IllegalArgumentException("Unknown wire source '" + prefix + "' in " + spec);
        };
    }

    @Override
    public String toString() {
        return (source == Source.V7 ? "v7:" : "qs:") + path;
    }
}
