package io.github.dimazigel.yfinance.api;

import io.github.dimazigel.yfinance.dto.timeseries.TimeseriesResponse;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Retrofit binding for Yahoo's fundamentals timeseries endpoint. */
public interface FundamentalsApi {

    @GET("ws/fundamentals-timeseries/v1/finance/timeseries/{symbol}")
    TimeseriesResponse timeseries(
            @Path("symbol") String symbol,
            @Query("type") String type,
            @Query("period1") long period1,
            @Query("period2") long period2);
}
