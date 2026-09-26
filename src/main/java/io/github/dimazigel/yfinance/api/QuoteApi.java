package io.github.dimazigel.yfinance.api;

import com.fasterxml.jackson.databind.JsonNode;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit binding for Yahoo's batch quote endpoint. Unlike quoteSummary it serves every asset
 * class (indices, ETFs, crypto, FX, futures) and many symbols per request; unknown symbols are
 * simply omitted from the result rather than reported as an error.
 */
public interface QuoteApi {

    /** Raw rows for the assembler; {@code quoteResponse.result} is an array, absent symbols are omitted. */
    @GET("v7/finance/quote")
    JsonNode quoteRows(@Query("symbols") String symbols, @Query("formatted") boolean formatted);
}
