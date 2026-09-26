package io.github.dimazigel.yfinance.testsupport;

import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.http.YahooJsonMapper;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/** Real captured responses from src/test/resources/fixtures/instruments (see CaptureInstrumentFixtures). */
public final class InstrumentFixtures {

    private static final JsonMapper JSON = YahooJsonMapper.create();

    private InstrumentFixtures() {}

    public static String safe(String symbol) {
        return symbol.replaceAll("[^A-Za-z0-9]", "_");
    }

    /**
     * The single row of the captured v7 response for {@code symbol}; a {@link MissingNode} when no
     * such symbol was captured at all (Yahoo's own batch response simply omits an unknown symbol).
     */
    public static JsonNode v7Row(String symbol) {
        String body;
        try {
            body = Fixtures.load("instruments/v7_" + safe(symbol) + ".json");
        } catch (IllegalArgumentException noFixture) {
            return MissingNode.getInstance();
        }
        return JSON.readTree(body).path("quoteResponse").path("result").path(0);
    }

    /** All modules of the captured quoteSummary response; empty map for a 404 capture. */
    public static Map<String, JsonNode> qsModules(String symbol) {
        JsonNode first = JSON.readTree(Fixtures.load("instruments/qs_" + safe(symbol) + ".json"))
                .path("quoteSummary").path("result").path(0);
        var modules = new LinkedHashMap<String, JsonNode>();
        first.properties().forEach(e -> modules.put(e.getKey(), e.getValue()));
        return modules;
    }

    public static Payload payload(String symbol, boolean withModules) {
        return new Payload(Symbol.of(symbol), Optional.of(v7Row(symbol)), withModules ? qsModules(symbol) : Map.of());
    }

    /** A v7 response body containing the captured rows for the given symbols, for MockWebServer. */
    public static String v7Response(String... symbols) {
        var rows = JSON.createArrayNode();
        for (String s : symbols) {
            JsonNode row = v7Row(s);
            if (!row.isMissingNode()) {
                rows.add(row);
            }
        }
        var body = JSON.createObjectNode();
        body.putObject("quoteResponse").set("result", rows);
        return body.toString();
    }
}
