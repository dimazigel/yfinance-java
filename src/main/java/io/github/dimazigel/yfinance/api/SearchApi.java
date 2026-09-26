package io.github.dimazigel.yfinance.api;

import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.dto.search.SearchResponse;

/** Feign binding for Yahoo's search endpoint. */
public interface SearchApi {

    @RequestLine("GET /v1/finance/search?q={q}&quotesCount={quotesCount}&newsCount={newsCount}&enableFuzzyQuery={enableFuzzyQuery}")
    SearchResponse search(
            @Param("q") String query,
            @Param("quotesCount") int quotesCount,
            @Param("newsCount") int newsCount,
            @Param("enableFuzzyQuery") boolean enableFuzzyQuery);
}
