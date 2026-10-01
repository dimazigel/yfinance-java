package io.github.dimazigel.yfinance.internal.api;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.internal.dto.screener.ScreenerRequest;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Feign binding for Yahoo's screener. Both calls answer {@code finance.result[0]} with the page's
 * {@code quotes} as full v7 quote rows, which is why they return {@code JsonNode}: the rows go to
 * the same assembler as {@code QuoteApi}'s.
 */
public interface ScreenerApi {

    /** A saved screen. Paging is {@code start}: this endpoint ignores {@code offset}. */
    @RequestLine("GET /v1/finance/screener/predefined/saved?scrIds={scrIds}&count={count}&start={start}"
            + "&sortField={sortField}&sortType={sortType}&formatted=false&corsDomain=finance.yahoo.com&lang=en-US&region=US")
    JsonNode predefined(
            @Param("scrIds") String id,
            @Param("count") int count,
            @Param("start") int start,
            @Param("sortField") @Nullable String sortField,
            @Param("sortType") @Nullable String sortType);

    @RequestLine("POST /v1/finance/screener?formatted=false&corsDomain=finance.yahoo.com&lang=en-US&region=US")
    @Headers("Content-Type: application/json")
    JsonNode custom(ScreenerRequest body);
}
