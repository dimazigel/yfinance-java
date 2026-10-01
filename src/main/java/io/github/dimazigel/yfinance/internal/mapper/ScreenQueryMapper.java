package io.github.dimazigel.yfinance.internal.mapper;

import io.github.dimazigel.yfinance.screener.ScreenQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns a {@link ScreenQuery} into the operator tree Yahoo's screener takes: {@code {operator, operands}}, nested. */
public final class ScreenQueryMapper {

    private ScreenQueryMapper() {}

    public static Map<String, Object> toWire(ScreenQuery<?> query) {
        var node = new LinkedHashMap<String, Object>();
        var operands = new ArrayList<Object>();
        switch (query) {
            case ScreenQuery.Condition<?> condition -> {
                node.put("operator", wire(condition.operator()));
                operands.add(condition.field().key());
                operands.addAll(condition.values());
            }
            case ScreenQuery.Group<?> group -> {
                node.put("operator", group.logic().name());
                for (ScreenQuery<?> operand : group.queries()) {
                    operands.add(toWire(operand));
                }
            }
        }
        node.put("operands", List.copyOf(operands));
        return node;
    }

    private static String wire(ScreenQuery.Operator operator) {
        return operator == ScreenQuery.Operator.BETWEEN ? "BTWN" : operator.name();
    }
}
