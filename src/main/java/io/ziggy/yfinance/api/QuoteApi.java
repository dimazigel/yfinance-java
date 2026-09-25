package io.ziggy.yfinance.api;

import io.ziggy.yfinance.dto.quote.QuoteResponse;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit binding for Yahoo's batch quote endpoint. Unlike quoteSummary it serves every asset
 * class (indices, ETFs, crypto, FX, futures) and many symbols per request; unknown symbols are
 * simply omitted from the result rather than reported as an error.
 */
public interface QuoteApi {

    @GET("v7/finance/quote")
    QuoteResponse quote(@Query("symbols") String symbols, @Query("formatted") boolean formatted);
}
