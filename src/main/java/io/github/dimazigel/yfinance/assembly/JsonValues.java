package io.github.dimazigel.yfinance.assembly;

import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;

/**
 * The single place Yahoo's "number or numeric string" convention is decoded: most fields arrive as
 * a JSON number, but some arrive as a string ({@code "1234567"}). A number is read through Jackson's
 * typed accessor; a string is stripped and parsed strictly, so a non-numeric value or (for {@link
 * #toLong}/{@link #toInt}) a fractional string throws rather than silently reading as {@code 0}.
 */
public final class JsonValues {

    private JsonValues() {}

    /** {@code node} as a {@code long}: the typed accessor for a number, else a stripped, strictly-parsed string. */
    public static long toLong(JsonNode node) {
        return node.isNumber() ? node.longValue() : Long.parseLong(node.asString().strip());
    }

    /** {@code node} as an {@code int}: the typed accessor for a number, else a stripped, strictly-parsed string. */
    public static int toInt(JsonNode node) {
        return node.isNumber() ? node.intValue() : Integer.parseInt(node.asString().strip());
    }

    /** {@code node} as a {@link BigDecimal}: the typed accessor for a number, else a stripped, strictly-parsed string. */
    public static BigDecimal toDecimal(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : new BigDecimal(node.asString().strip());
    }
}
