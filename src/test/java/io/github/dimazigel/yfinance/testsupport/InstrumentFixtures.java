package io.github.dimazigel.yfinance.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.http.YahooObjectMapper;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Real captured responses from src/test/resources/fixtures/instruments (see CaptureInstrumentFixtures). */
public final class InstrumentFixtures {

    private static final ObjectMapper JSON = YahooObjectMapper.create();

    private InstrumentFixtures() {}

    public static String safe(String symbol) {
        return symbol.replaceAll("[^A-Za-z0-9]", "_");
    }

    /** The single row of the captured v7 response for {@code symbol}. */
    public static JsonNode v7Row(String symbol) {
        try {
            JsonNode result = JSON.readTree(Fixtures.load("instruments/v7_" + safe(symbol) + ".json"))
                    .path("quoteResponse").path("result");
            return result.path(0);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** All modules of the captured quoteSummary response; empty map for a 404 capture. */
    public static Map<String, JsonNode> qsModules(String symbol) {
        try {
            JsonNode first = JSON.readTree(Fixtures.load("instruments/qs_" + safe(symbol) + ".json"))
                    .path("quoteSummary").path("result").path(0);
            var modules = new LinkedHashMap<String, JsonNode>();
            first.fields().forEachRemaining(e -> modules.put(e.getKey(), e.getValue()));
            return modules;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
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
