package io.github.dimazigel.yfinance.api;

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
}
