package io.github.dimazigel.yfinance.assembly;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Executes a field table against a payload: first source that has the value wins. */
public final class Resolver {

    private Resolver() {}

    public static Resolved resolve(Payload payload, List<FieldSpec> specs) {
        var byName = new LinkedHashMap<String, FieldSpec>();
        var values = new LinkedHashMap<String, JsonNode>();
        var units = new LinkedHashMap<String, Unit>();
        var missing = new ArrayList<String>();
        for (FieldSpec spec : specs) {
            byName.put(spec.name(), spec);
            boolean resolved = false;
            for (WirePath path : spec.paths()) {
                Optional<JsonNode> found = payload.find(path);
                if (found.isPresent()) {
                    values.put(spec.name(), found.get());
                    units.put(spec.name(), path.unit().orElse(spec.unit()));
                    resolved = true;
                    break;
                }
            }
            if (!resolved && spec.kind() == Kind.REQUIRED) {
                missing.add(spec.name());
            }
        }
        return new Resolved(byName, values, units, missing);
    }
}
