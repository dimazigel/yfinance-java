package io.github.dimazigel.yfinance.api;

import io.github.dimazigel.yfinance.dto.search.SearchResponse;
import retrofit2.http.GET;
import retrofit2.http.Query;

/** Retrofit binding for Yahoo's search endpoint. */
public interface SearchApi {

    @GET("v1/finance/search")
    SearchResponse search(
            @Query("q") String query,
            @Query("quotesCount") int quotesCount,
            @Query("newsCount") int newsCount,
            @Query("enableFuzzyQuery") boolean enableFuzzyQuery);
}
