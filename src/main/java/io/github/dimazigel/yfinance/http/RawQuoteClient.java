package io.github.dimazigel.yfinance.http;

import io.github.dimazigel.yfinance.api.QuoteApi;
import io.github.dimazigel.yfinance.api.QuoteSummaryApi;
import io.github.dimazigel.yfinance.exception.YFHttpException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * Fetches the raw JSON the assembler works on: batched v7 rows and per-symbol quoteSummary modules.
 *
 * <p><strong>Internal to the library — not API; may change without notice.</strong> {@code public}
 * only because the services live in another package; it exposes Jackson's {@code JsonNode}.
 */
public final class RawQuoteClient {

    /** Verified in the field survey: Yahoo accepts 100 symbols per v7 request. */
    static final int CHUNK = 100;
    private static final String CORS_DOMAIN = "finance.yahoo.com";

    private final QuoteApi quoteApi;
    private final QuoteSummaryApi quoteSummaryApi;

    public RawQuoteClient(QuoteApi quoteApi, QuoteSummaryApi quoteSummaryApi) {
        this.quoteApi = quoteApi;
        this.quoteSummaryApi = quoteSummaryApi;
    }

    /**
     * One row per known symbol, keyed by the requested {@link Symbol}; unknown symbols are simply
     * absent. Rows are matched by the symbol Yahoo echoes, upper-cased, so a request for {@code aapl}
     * finds the {@code AAPL} row; a symbol Yahoo would answer under a different spelling (an alias)
     * is not matched and comes back as unknown.
     */
    public Map<Symbol, JsonNode> quoteRows(List<Symbol> symbols) {
        var rows = new LinkedHashMap<Symbol, JsonNode>();
        List<Symbol> distinct = symbols.stream().distinct().toList();
        for (int i = 0; i < distinct.size(); i += CHUNK) {
            List<Symbol> chunk = distinct.subList(i, Math.min(i + CHUNK, distinct.size()));
            String joined = chunk.stream().map(Symbol::value).collect(Collectors.joining(","));
            JsonNode result = quoteApi.quoteRows(joined, false).path("quoteResponse").path("result");
            for (JsonNode row : result) {
                String reported = row.path("symbol").asText("");
                if (!reported.isBlank()) {
                    rows.put(Symbol.of(reported), row); // Symbol.of upper-cases, matching the requested key
                }
            }
        }
        return rows;
    }

    /**
     * The requested modules for one symbol (module name → object), or empty when Yahoo has no
     * result for it — a 404, or a 200 whose {@code result} is null or empty; the two are not
     * distinguished.
     */
    public Optional<Map<String, JsonNode>> modules(Symbol symbol, Collection<String> moduleNames) {
        String joined = String.join(",", moduleNames);
        JsonNode response;
        try {
            response = quoteSummaryApi.modules(symbol.value(), joined, false, CORS_DOMAIN);
        } catch (YFHttpException e) {
            if (e.status() == 404) {
                return Optional.empty();
            }
            throw e;
        }
        JsonNode first = response.path("quoteSummary").path("result").path(0);
        if (first.isMissingNode() || !first.isObject()) {
            return Optional.empty();
        }
        var modules = new LinkedHashMap<String, JsonNode>();
        first.properties().forEach(entry -> modules.put(entry.getKey(), entry.getValue()));
        return Optional.of(modules);
    }
}
