package io.github.dimazigel.yfinance.moduletest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.dimazigel.yfinance.YFinance;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.instrument.Equity;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.HttpUrl;

/**
 * Runs the library as a named module, the way a JPMS consumer does, against a local stub server
 * that replays the unit-test fixtures. The unit tests run on the classpath, where strong
 * encapsulation is off, so only this catches a package {@code module-info.java} forgot to export
 * or to open to Jackson, or a Feign proxy that cannot be created inside the module.
 *
 * <p>Every endpoint family is exercised once: raw {@code JsonNode} responses (quote, quoteSummary),
 * record DTOs (chart, options, search), the any-setter DTO (timeseries) and the encoded request
 * body (news).
 */
public final class ModulePathSmoke {

    private ModulePathSmoke() {}

    public static void main(String[] args) throws Exception {
        Path fixtures = Path.of(args[0]);
        require(YFinance.class.getModule().isNamed(), "the library is not running as a named module");

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, fixtures));
        server.start();
        try {
            HttpUrl base = HttpUrl.get("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            try (var yf = YFinance.create(EndpointConfig.production().withHosts(base))) {
                var aapl = yf.ticker("AAPL");
                Equity equity = aapl.as(Equity.class);
                require(equity.valuation().marketCap().signum() > 0, "snapshot");
                require(!aapl.detail(equity).profile().sector().isBlank(), "detail");
                require(!aapl.history(Range.ONE_MONTH, Interval.ONE_DAY).bars().isEmpty(), "history");
                require(!aapl.statements(equity, StatementType.INCOME, Frequency.ANNUAL).periods().isEmpty(), "statements");
                require(!aapl.valuationHistory(equity).isEmpty(), "valuation history");
                require(aapl.options().isPresent(), "options");
                require(!aapl.news().isEmpty(), "news");
                require(!yf.search("apple").quotes().isEmpty(), "search");
            }
        } finally {
            server.stop(0);
        }
        System.out.println("module path smoke: OK (" + YFinance.class.getModule().getName() + ")");
    }

    private static void respond(HttpExchange exchange, Path fixtures) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = String.valueOf(exchange.getRequestURI().getQuery());
        exchange.getRequestBody().readAllBytes();
        String fixture;
        if (path.equals("/v1/test/getcrumb")) {
            send(exchange, 200, "text/plain", "mock-crumb".getBytes());
            return;
        } else if (path.equals("/v7/finance/quote")) {
            fixture = "instruments/v7_AAPL.json";
        } else if (path.startsWith("/v10/finance/quoteSummary/")) {
            fixture = "instruments/qs_AAPL.json";
        } else if (path.startsWith("/v8/finance/chart/")) {
            fixture = "chart_aapl_1d.json";
        } else if (path.startsWith("/ws/fundamentals-timeseries/")) {
            fixture = query.contains("PeRatio") ? "timeseries_valuation_quarterly_aapl.json" : "timeseries_income_annual.json";
        } else if (path.startsWith("/v7/finance/options/")) {
            fixture = "options/options_AAPL.json";
        } else if (path.equals("/xhr/ncp")) {
            fixture = "news/ncp_news_AAPL.json";
        } else if (path.equals("/v1/finance/search")) {
            fixture = "search_apple.json";
        } else {
            send(exchange, 404, "application/json", "{}".getBytes());   // the cookie seed; the handshake tolerates it
            return;
        }
        send(exchange, 200, "application/json", Files.readAllBytes(fixtures.resolve(fixture)));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void require(boolean condition, String what) {
        if (!condition) {
            throw new IllegalStateException("module path smoke failed: " + what);
        }
    }
}
