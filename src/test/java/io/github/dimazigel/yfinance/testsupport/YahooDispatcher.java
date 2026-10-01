package io.github.dimazigel.yfinance.testsupport;

import io.github.dimazigel.yfinance.enums.LineItem;
import io.github.dimazigel.yfinance.enums.StatementType;
import java.util.Arrays;
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
 *   <li>search and lookup: the single fixture each
 *   <li>{@code /xhr/ncp} (the news stream): the AAPL capture of the requested tab for every symbol
 *       except {@link #UNKNOWN}, which gets the empty-stream capture
 *   <li>{@code /ws/fundamentals-timeseries/}: {@code type=shares_out} (batch E/1, item 4) gets the
 *       shares-outstanding capture; the valuation-measure keys (batch E/2) get the AAPL valuation
 *       capture of their frequency, quarterly or annual; a chunk whose keys are purely annual-income ones
 *       (a chunk never spills past {@code FundamentalsService.MAX_KEYS_PER_REQUEST} keys without
 *       picking up a key from another frequency or statement) gets the single-statement annual
 *       income capture; everything else (a quarterly or trailing key present, or a balance-sheet or
 *       cash-flow key, however the chunking split it) gets the combined capture (annual, quarterly
 *       and trailing series of all three statements). Two more timeseries fixtures
 *       ({@code timeseries_multi_chunk1.json}/{@code _chunk2.json}) exist for
 *       {@code FundamentalsServiceTest}'s two-chunk-merge test; that test enqueues them directly and
 *       never goes through this dispatcher.
 *   <li>{@code /v1/test/getcrumb}: a fixed crumb, so a {@code YFinance.create(config)} pointed at
 *       the mock server completes its handshake (the cookie URL falls through to the 404 below,
 *       which the handshake tolerates)
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
        if (path.equals("/xhr/ncp")) {
            return news(url.queryParameter("queryRef"), request.getBody().snapshot().utf8());
        }
        if (path.equals("/v1/test/getcrumb")) {
            return new MockResponse().setResponseCode(200).setBody("mock-crumb");
        }
        if (path.startsWith("/ws/fundamentals-timeseries/")) {
            return timeseries(url.queryParameter("type"));
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

    /** See the class javadoc's {@code /ws/fundamentals-timeseries/} entry for the routing rules. */
    private static MockResponse timeseries(String type) {
        if (type == null) {
            return Fixtures.jsonResponse("timeseries_income_annual.json");
        }
        if (type.equals("shares_out")) {
            return Fixtures.jsonResponse("timeseries_shares_out_aapl.json");
        }
        if (type.contains("PeRatio")) {   // a valuation-measure request; no statement key contains this
            return Fixtures.jsonResponse(type.startsWith("annual")
                    ? "timeseries_valuation_annual_aapl.json" : "timeseries_valuation_quarterly_aapl.json");
        }
        boolean pureAnnualIncome = Arrays.stream(type.split(",")).allMatch(YahooDispatcher::isAnnualIncomeKey);
        return Fixtures.jsonResponse(pureAnnualIncome ? "timeseries_income_annual.json" : "timeseries_multi.json");
    }

    private static boolean isAnnualIncomeKey(String key) {
        return key.startsWith("annual") && LineItem.forStatement(StatementType.INCOME).stream()
                .anyMatch(li -> key.equals("annual" + li.key()));
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

    private static MockResponse news(String queryRef, String body) {
        if (body.contains(UNKNOWN)) {
            return Fixtures.jsonResponse("news/ncp_news_unknown.json");
        }
        String tab = switch (queryRef) {
            case "newsAll" -> "all";
            case "pressRelease" -> "press";
            default -> "news";
        };
        return Fixtures.jsonResponse("news/ncp_" + tab + "_AAPL.json");
    }

    private static MockResponse json(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }
}
