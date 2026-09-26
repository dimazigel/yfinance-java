package io.github.dimazigel.yfinance.assembly;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Field values after resolution, with typed, unit-aware access. Built only by {@link Resolver}. */
public final class Resolved {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Map<String, FieldSpec> specs;
    private final Map<String, JsonNode> values;
    private final Map<String, Unit> units; // effective unit: the winning path's override, else the field's
    private final List<String> missingRequired;

    Resolved(Map<String, FieldSpec> specs, Map<String, JsonNode> values, Map<String, Unit> units, List<String> missingRequired) {
        this.specs = Map.copyOf(specs);
        this.values = Map.copyOf(values);
        this.units = Map.copyOf(units);
        this.missingRequired = List.copyOf(missingRequired);
    }

    /** Names of REQUIRED fields no source supplied, in spec order. Empty means the record can be built. */
    public List<String> missingRequired() {
        return missingRequired;
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    /**
     * True when every field declared in {@code cluster} resolved. Throws {@link IllegalArgumentException} for
     * a cluster no spec declares, so a typo cannot pass as "present".
     */
    public boolean clusterPresent(String cluster) {
        List<FieldSpec> members = specs.values().stream()
                .filter(s -> s.cluster().filter(cluster::equals).isPresent())
                .toList();
        if (members.isEmpty()) {
            throw new IllegalArgumentException("no field declares cluster " + cluster);
        }
        return members.stream().allMatch(s -> values.containsKey(s.name()));
    }

    // ---- required accessors: absent -> IllegalStateException (check missingRequired() first)

    public JsonNode node(String name) {
        JsonNode node = values.get(name);
        if (node == null) {
            throw new IllegalStateException("Field '" + name + "' was not resolved; check missingRequired() before building");
        }
        return node;
    }

    public BigDecimal decimal(String name) {
        return convertDecimal(name, node(name));
    }

    public long longValue(String name) {
        return node(name).isNumber() ? node(name).longValue() : Long.parseLong(node(name).asText().strip());
    }

    public int intValue(String name) {
        return node(name).isNumber() ? node(name).intValue() : Integer.parseInt(node(name).asText().strip());
    }

    public String string(String name) {
        return node(name).asText();
    }

    public boolean bool(String name) {
        return node(name).asBoolean();
    }

    public Instant instant(String name) {
        return convertInstant(name, node(name));
    }

    public LocalDate date(String name) {
        return convertDate(name, node(name));
    }

    /** Elements of a LIST field; empty when Yahoo omitted it. */
    public List<JsonNode> list(String name) {
        JsonNode node = values.get(name);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var out = new ArrayList<JsonNode>();
        node.forEach(out::add);
        return List.copyOf(out);
    }

    // ---- optional accessors

    public Optional<BigDecimal> optDecimal(String name) {
        return opt(name, n -> convertDecimal(name, n));
    }

    public Optional<Long> optLong(String name) {
        return opt(name, n -> n.isNumber() ? n.longValue() : Long.parseLong(n.asText().strip()));
    }

    public Optional<Integer> optInt(String name) {
        return opt(name, n -> n.isNumber() ? n.intValue() : Integer.parseInt(n.asText().strip()));
    }

    public Optional<String> optString(String name) {
        return opt(name, JsonNode::asText);
    }

    public Optional<Instant> optInstant(String name) {
        return opt(name, n -> convertInstant(name, n));
    }

    public Optional<LocalDate> optDate(String name) {
        return opt(name, n -> convertDate(name, n));
    }

    private <T> Optional<T> opt(String name, Function<JsonNode, T> convert) {
        JsonNode node = values.get(name);
        return node == null ? Optional.empty() : Optional.of(convert.apply(node));
    }

    // ---- unit conversion

    private BigDecimal convertDecimal(String name, JsonNode node) {
        BigDecimal value = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText().strip());
        return unit(name) == Unit.PERCENT ? value.divide(HUNDRED, MathContext.DECIMAL64) : value;
    }

    private Instant convertInstant(String name, JsonNode node) {
        long n = node.longValue();
        return switch (unit(name)) {
            case EPOCH_MILLIS -> Instant.ofEpochMilli(n);
            case EPOCH_SECONDS, EPOCH_DATE, RAW -> Instant.ofEpochSecond(n);
            case ISO_DATE -> LocalDate.parse(node.asText()).atStartOfDay(ZoneOffset.UTC).toInstant();
            case PERCENT -> throw new IllegalStateException("Field '" + name + "' is a percent, not a time");
        };
    }

    private LocalDate convertDate(String name, JsonNode node) {
        return switch (unit(name)) {
            case ISO_DATE -> LocalDate.parse(node.asText().strip());
            case EPOCH_DATE, EPOCH_SECONDS -> LocalDate.ofInstant(Instant.ofEpochSecond(node.longValue()), ZoneOffset.UTC);
            case EPOCH_MILLIS -> LocalDate.ofInstant(Instant.ofEpochMilli(node.longValue()), ZoneOffset.UTC);
            case RAW, PERCENT -> throw new IllegalStateException("Field '" + name + "' has no date unit");
        };
    }

    private Unit unit(String name) {
        return units.getOrDefault(name, Unit.RAW);
    }
}
