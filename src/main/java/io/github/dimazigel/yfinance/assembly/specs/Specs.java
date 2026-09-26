package io.github.dimazigel.yfinance.assembly.specs;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Helper for concatenating spec lists. */
final class Specs {

    private Specs() {}

    @SafeVarargs
    static List<FieldSpec> concat(List<FieldSpec>... parts) {
        return java.util.Arrays.stream(parts).flatMap(List::stream).toList();
    }
}
