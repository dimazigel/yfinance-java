package io.github.dimazigel.yfinance.assembly.build;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helpers over JsonNode, unwrapping {raw, fmt} like Payload.present. The lenient helpers ({@link
 * #uri}, {@link #isoDate}) log what they drop at DEBUG; the numeric ones parse strictly, like
 * {@code Resolved}, so a non-numeric string fails instead of reading as 0.
 */
final class Nodes {

    private static final Logger LOG = LoggerFactory.getLogger(Nodes.class);

    private Nodes() {}

    static Optional<JsonNode> get(JsonNode node, String key) {
        JsonNode v = node.path(key);
        if (v.isObject() && v.has("raw")) v = v.get("raw");
        if (v.isMissingNode() || v.isNull() || (v.isTextual() && v.asText().isBlank()) || ((v.isObject() || v.isArray()) && v.isEmpty())) return Optional.empty();
        return Optional.of(v);
    }

    static BigDecimal decimal(JsonNode node, String key) {
        return get(node, key).map(Nodes::toDecimal).orElseThrow(() -> new IllegalStateException("missing " + key));
    }

    static Optional<BigDecimal> optDecimal(JsonNode node, String key) {
        return get(node, key).map(Nodes::toDecimal);
    }

    static Optional<Long> optLong(JsonNode node, String key) {
        return get(node, key).map(v -> v.isNumber() ? v.longValue() : Long.parseLong(v.asText().strip()));
    }

    static Optional<Integer> optInt(JsonNode node, String key) {
        return get(node, key).map(v -> v.isNumber() ? v.intValue() : Integer.parseInt(v.asText().strip()));
    }

    static String string(JsonNode node, String key) {
        return get(node, key).map(JsonNode::asText).orElseThrow(() -> new IllegalStateException("missing " + key));
    }

    static Optional<String> optString(JsonNode node, String key) {
        return get(node, key).map(JsonNode::asText);
    }

    static Optional<Instant> optInstantSeconds(JsonNode node, String key) {
        return get(node, key).map(v -> Instant.ofEpochSecond(v.asLong()));
    }

    static Optional<LocalDate> optDateSeconds(JsonNode node, String key) {
        return optInstantSeconds(node, key).map(i -> LocalDate.ofInstant(i, ZoneOffset.UTC));
    }

    /** An ISO date such as {@code 2026-09-30}; empty if absent or unparseable. */
    static Optional<LocalDate> optIsoDate(JsonNode node, String key) {
        return optString(node, key).flatMap(Nodes::isoDate);
    }

    private static Optional<LocalDate> isoDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value.strip()));
        } catch (DateTimeParseException e) {
            LOG.atDebug().addKeyValue("value", value).addKeyValue("reason", e.getMessage())
                    .log("Dropped unparseable ISO date '{}'", value);
            return Optional.empty();
        }
    }

    static Optional<URI> optUri(JsonNode node, String key) {
        return optString(node, key).flatMap(Nodes::uri);
    }

    /** A URI, or empty (logged at DEBUG) when Yahoo's text is not one; callers of optional URL fields use this. */
    static Optional<URI> uri(String s) {
        try {
            return Optional.of(new URI(s));
        } catch (URISyntaxException e) {
            LOG.atDebug().addKeyValue("value", s).addKeyValue("reason", e.getReason())
                    .log("Dropped malformed URI '{}': {}", s, e.getReason());
            return Optional.empty();
        }
    }

    private static BigDecimal toDecimal(JsonNode v) {
        return v.isNumber() ? v.decimalValue() : new BigDecimal(v.asText().strip());
    }

    /** Yahoo's "[{key: value}, ...]" lists: one map entry per element. */
    static List<Map.Entry<String, BigDecimal>> singleKeyList(List<JsonNode> nodes) {
        var out = new ArrayList<Map.Entry<String, BigDecimal>>();
        for (JsonNode n : nodes) {
            var it = n.fields();
            if (it.hasNext()) {
                var e = it.next();
                get(n, e.getKey()).ifPresent(v -> out.add(Map.entry(e.getKey(), toDecimal(v))));
            }
        }
        return List.copyOf(out);
    }
}
