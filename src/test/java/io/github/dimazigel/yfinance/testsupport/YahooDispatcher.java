package io.github.dimazigel.yfinance.testsupport;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Routes every Yahoo endpoint the facade touches to the captured fixtures, so facade tests run a
 * whole {@code YFinance} over real payloads without enqueuing responses by hand:
 *
 * <ul>
 *   <li>{@code /v7/finance/quote}: the captured v7 rows for whatever symbols were asked (unknown
 *       symbols are omitted, as Yahoo does)
 *   <li>{@code /v10/finance/quoteSummary/{symbol}}: the captured per-symbol modules, 404 when the
 *       capture itself was a 404 or no capture exists
 *   <li>{@code /v8/finance/chart/{symbol}}: the AAPL daily chart for every symbol except
 *       {@link #UNKNOWN}, which gets Yahoo's "Not Found" envelope
 *   <li>{@code /v7/finance/options/{symbol}}: the captured chain for that symbol (and expiration,
 *       when {@code date} is given), or the no-listed-options capture for anything else
 *   <li>search, lookup and fundamentals timeseries: the single fixture each
 * </ul>
 */
public class YahooDispatcher extends Dispatcher {

    /** A symbol no capture knows: absent from v7, 404 on quoteSummary, "Not Found" on chart. */
    public static final String UNKNOWN = "NO_SUCH_SYMBOL_XYZ";

    @Override
    public MockResponse dispatch(RecordedRequest request) {
        HttpUrl url = request.getRequestUrl();
        String path = url.encodedPath();
        if (path.equals("/v7/finance/quote")) {
            return json(InstrumentFixtures.v7Response(url.queryParameter("symbols").split(",")));
        }
        if (path.equals("/v1/finance/search")) {
            return Fixtures.jsonResponse("search_apple.json");
        }
        if (path.equals("/v1/finance/lookup")) {
            return Fixtures.jsonResponse("lookup_apple.json");
        }
        if (path.startsWith("/ws/fundamentals-timeseries/")) {
            return Fixtures.jsonResponse("timeseries_income_annual.json");
        }
        String symbol = url.pathSegments().getLast();
        if (path.startsWith("/v10/finance/quoteSummary/")) {
            return quoteSummary(symbol);
        }
        if (path.startsWith("/v8/finance/chart/")) {
            return chart(symbol);
        }
        if (path.startsWith("/v7/finance/options/")) {
            return options(symbol, url.queryParameter("date"));
        }
        return new MockResponse().setResponseCode(404).setBody("{}");
    }

    private static MockResponse quoteSummary(String symbol) {
        try {
            String body = Fixtures.load("instruments/qs_" + InstrumentFixtures.safe(symbol) + ".json");
            return new MockResponse().setResponseCode(body.contains("\"result\":null") ? 404 : 200).setBody(body);
        } catch (IllegalArgumentException noFixture) {
            return new MockResponse().setResponseCode(404).setBody(
                    "{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"Quote not found for symbol: "
                            + symbol + "\"}}}");
        }
    }

    private static MockResponse chart(String symbol) {
        if (symbol.equals(UNKNOWN)) {
            return new MockResponse().setResponseCode(404).setBody(
                    "{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"No data found, symbol may be delisted\"}}}");
        }
        return Fixtures.jsonResponse("chart_aapl_1d.json");
    }

    private static MockResponse options(String symbol, String date) {
        String base = "options/options_" + InstrumentFixtures.safe(symbol);
        if (date != null) {
            try {
                return Fixtures.jsonResponse(base + "_" + date + ".json");
            } catch (IllegalArgumentException noExpirationCapture) {
                // fall through to the symbol's nearest-expiration capture
            }
        }
        try {
            return Fixtures.jsonResponse(base + ".json");
        } catch (IllegalArgumentException noFixture) {
            return Fixtures.jsonResponse("options/options_empty.json");
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }
}
