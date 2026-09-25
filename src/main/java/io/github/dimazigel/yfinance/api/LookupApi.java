package io.github.dimazigel.yfinance.api;

import io.github.dimazigel.yfinance.dto.lookup.LookupResponse;
import retrofit2.http.GET;
import retrofit2.http.Query;

/** Retrofit binding for Yahoo's lookup endpoint. */
public interface LookupApi {

    @GET("v1/finance/lookup")
    LookupResponse lookup(
            @Query("query") String query,
            @Query("type") String type,
            @Query("start") int start,
            @Query("count") int count,
            @Query("formatted") boolean formatted,
            @Query("fetchPricingData") boolean fetchPricingData);
}
