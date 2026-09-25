package io.github.dimazigel.yfinance.api;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.http.SyncCallAdapterFactory;
import io.github.dimazigel.yfinance.http.YahooObjectMapper;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Bundle of the Retrofit interfaces. Most endpoints live on the primary host; the fundamentals
 * timeseries endpoint is served from the secondary host.
 */
public record YahooApis(
        ChartApi chart,
        QuoteSummaryApi quoteSummary,
        QuoteApi quote,
        FundamentalsApi fundamentals,
        OptionsApi options,
        SearchApi search,
        LookupApi lookup) {

    /** Builds all interfaces, sourcing fundamentals from {@code fundamentalsRetrofit}. */
    public static YahooApis create(Retrofit primary, Retrofit fundamentalsRetrofit) {
        return new YahooApis(
                primary.create(ChartApi.class),
                primary.create(QuoteSummaryApi.class),
                primary.create(QuoteApi.class),
                fundamentalsRetrofit.create(FundamentalsApi.class),
                primary.create(OptionsApi.class),
                primary.create(SearchApi.class),
                primary.create(LookupApi.class));
    }

    /** Builds all interfaces from the given client and host configuration. */
    public static YahooApis create(EndpointConfig config, OkHttpClient client) {
        var converter = JacksonConverterFactory.create(YahooObjectMapper.create());
        var callAdapter = SyncCallAdapterFactory.create();
        Retrofit primary = new Retrofit.Builder()
                .baseUrl(config.query1Base())
                .client(client)
                .addCallAdapterFactory(callAdapter)
                .addConverterFactory(converter)
                .build();
        Retrofit secondary = new Retrofit.Builder()
                .baseUrl(config.query2Base())
                .client(client)
                .addCallAdapterFactory(callAdapter)
                .addConverterFactory(converter)
                .build();
        return create(primary, secondary);
    }
}
