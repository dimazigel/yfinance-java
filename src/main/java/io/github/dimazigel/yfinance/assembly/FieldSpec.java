package io.github.dimazigel.yfinance.assembly;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One row of Appendix A: a model field, its kind, its wire paths in precedence order, its unit. */
public record FieldSpec(String name, Kind kind, Optional<String> cluster, List<WirePath> paths, Unit unit) {

    public FieldSpec {
        Objects.requireNonNull(name, "name");
        paths = List.copyOf(paths);
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Field " + name + " needs at least one wire path");
        }
    }

    public static FieldSpec required(String name, Unit unit, String... paths) {
        return new FieldSpec(name, Kind.REQUIRED, Optional.empty(), parse(paths), unit);
    }

    public static FieldSpec optional(String name, Unit unit, String... paths) {
        return new FieldSpec(name, Kind.OPTIONAL, Optional.empty(), parse(paths), unit);
    }

    /** An optional field that is only present when every member of {@code cluster} is. */
    public static FieldSpec clustered(String cluster, String name, Unit unit, String... paths) {
        return new FieldSpec(name, Kind.OPTIONAL, Optional.of(cluster), parse(paths), unit);
    }

    /**
     * A required field that also belongs to a named group, for documentation symmetry with the
     * appendix ({@code C:name(R)}). Required clusters are never checked with {@link
     * Resolved#clusterPresent}: every member is REQUIRED, so an absent member already shows up in
     * {@link Resolved#missingRequired()} and the record is not built at all.
     */
    public static FieldSpec requiredCluster(String cluster, String name, Unit unit, String... paths) {
        return new FieldSpec(name, Kind.REQUIRED, Optional.of(cluster), parse(paths), unit);
    }

    public static FieldSpec list(String name, String... paths) {
        return new FieldSpec(name, Kind.LIST, Optional.empty(), parse(paths), Unit.RAW);
    }

    private static List<WirePath> parse(String[] paths) {
        return Arrays.stream(paths).map(WirePath::parse).toList();
    }
}
