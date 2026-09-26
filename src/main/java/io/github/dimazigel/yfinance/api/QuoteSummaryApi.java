package io.github.dimazigel.yfinance.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.dimazigel.yfinance.dto.quotesummary.QuoteSummaryResponse;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Retrofit binding for Yahoo's quoteSummary endpoint. */
public interface QuoteSummaryApi {

    @GET("v10/finance/quoteSummary/{symbol}")
    QuoteSummaryResponse quoteSummary(
            @Path("symbol") String symbol,
            @Query("modules") String modules,
            @Query("formatted") boolean formatted,
            @Query("corsDomain") String corsDomain);

    /** Raw modules for the assembler; {@code quoteSummary.result[0]} maps module name to object. */
    @GET("v10/finance/quoteSummary/{symbol}")
    JsonNode modules(@Path("symbol") String symbol, @Query("modules") String modules,
            @Query("formatted") boolean formatted, @Query("corsDomain") String corsDomain);
}
