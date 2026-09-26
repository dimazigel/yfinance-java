package io.github.dimazigel.yfinance.api;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.http.YahooFeign;
import okhttp3.OkHttpClient;

/**
 * The Feign interfaces for Yahoo's endpoints, bundled so {@code YFinance} can be wired from one object.
 * Fundamentals timeseries is served by the query2 host; everything else by query1.
 */
public record YahooApis(
        ChartApi chart,
        QuoteSummaryApi quoteSummary,
        QuoteApi quote,
        FundamentalsApi fundamentals,
        OptionsApi options,
        SearchApi search,
        LookupApi lookup) {

    /** Builds all interfaces from the given client and host configuration. */
    public static YahooApis create(EndpointConfig config, OkHttpClient client) {
        YahooFeign feign = YahooFeign.of(client);
        return new YahooApis(
                feign.target(ChartApi.class, config.query1Base()),
                feign.target(QuoteSummaryApi.class, config.query1Base()),
                feign.target(QuoteApi.class, config.query1Base()),
                feign.target(FundamentalsApi.class, config.query2Base()),
                feign.target(OptionsApi.class, config.query1Base()),
                feign.target(SearchApi.class, config.query1Base()),
                feign.target(LookupApi.class, config.query1Base()));
    }
}
