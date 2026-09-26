package io.github.dimazigel.yfinance.assembly.build;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Helpers over JsonNode, unwrapping {raw, fmt} like Payload.present. */
final class Nodes {

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
        return get(node, key).map(JsonNode::asLong);
    }

    static Optional<Integer> optInt(JsonNode node, String key) {
        return get(node, key).map(JsonNode::asInt);
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

    static Optional<URI> optUri(JsonNode node, String key) {
        return optString(node, key).flatMap(Nodes::uri);
    }

    static Optional<URI> uri(String s) {
        try {
            return Optional.of(new URI(s));
        } catch (URISyntaxException e) {
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
