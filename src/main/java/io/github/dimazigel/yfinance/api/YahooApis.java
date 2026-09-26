package io.github.dimazigel.yfinance.api;

import feign.Feign;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.http.YahooFeign;
import io.github.dimazigel.yfinance.http.YahooJsonMapper;
import okhttp3.HttpUrl;
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
        Feign.Builder feign = YahooFeign.builder(client, YahooJsonMapper.create());
        String query1 = base(config.query1Base());
        String query2 = base(config.query2Base());
        return new YahooApis(
                feign.target(ChartApi.class, query1),
                feign.target(QuoteSummaryApi.class, query1),
                feign.target(QuoteApi.class, query1),
                feign.target(FundamentalsApi.class, query2),
                feign.target(OptionsApi.class, query1),
                feign.target(SearchApi.class, query1),
                feign.target(LookupApi.class, query1));
    }

    /** Feign joins {@code base + template}; the templates start with {@code /}, so the base must not end with one. */
    private static String base(HttpUrl url) {
        String s = url.toString();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
