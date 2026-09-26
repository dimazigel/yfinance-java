package io.github.dimazigel.yfinance.assembly;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The raw JSON Yahoo returned for one symbol: its v7 quote row and any quoteSummary modules. */
public final class Payload {

    private final Symbol symbol;
    private final Optional<JsonNode> v7Row;
    private final Map<String, JsonNode> modules;

    public Payload(Symbol symbol, Optional<JsonNode> v7Row, Map<String, JsonNode> modules) {
        this.symbol = symbol;
        this.v7Row = v7Row;
        this.modules = Map.copyOf(modules);
    }

    public Symbol symbol() {
        return symbol;
    }

    public boolean hasModules() {
        return !modules.isEmpty();
    }

    /** A copy with {@code more} modules added (fallback or detail fetch); existing modules are kept. */
    public Payload withModules(Map<String, JsonNode> more) {
        var merged = new HashMap<>(modules);
        merged.putAll(more);
        return new Payload(symbol, v7Row, merged);
    }

    /** The value at {@code path}, unwrapping Yahoo's {@code {raw, fmt}} objects; empty when absent/blank. */
    public Optional<JsonNode> find(WirePath path) {
        JsonNode cursor;
        String[] segments = path.path().split("\\.", -1);
        int first;
        if (path.source() == Source.V7) {
            if (v7Row.isEmpty()) {
                return Optional.empty();
            }
            cursor = v7Row.get();
            first = 0;
        } else {
            JsonNode module = modules.get(segments[0]);
            if (module == null) {
                return Optional.empty();
            }
            cursor = module;
            first = 1;
        }
        for (int i = first; i < segments.length; i++) {
            String segment = segments[i];
            if (cursor.isArray() && segment.chars().allMatch(Character::isDigit)) {
                cursor = cursor.path(Integer.parseInt(segment));
            } else {
                cursor = cursor.path(segment);
            }
            if (cursor.isMissingNode()) {
                return Optional.empty();
            }
        }
        return present(cursor);
    }

    private static Optional<JsonNode> present(JsonNode node) {
        if (node.isObject() && node.has("raw")) {
            node = node.get("raw"); // Yahoo's {raw, fmt} shape
        }
        if (node.isNull() || node.isMissingNode()) {
            return Optional.empty();
        }
        if (node.isTextual() && node.asText().isBlank()) {
            return Optional.empty();
        }
        if ((node.isObject() || node.isArray()) && node.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(node);
    }
}
