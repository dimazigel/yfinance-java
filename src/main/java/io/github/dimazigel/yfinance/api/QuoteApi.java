package io.github.dimazigel.yfinance.api;

import feign.Param;
import feign.RequestLine;
import tools.jackson.databind.JsonNode;

/**
 * Feign binding for Yahoo's batch quote endpoint. Unlike quoteSummary it serves every asset
 * class (indices, ETFs, crypto, FX, futures) and many symbols per request; unknown symbols are
 * simply omitted from the result rather than reported as an error.
 */
public interface QuoteApi {

    /** Raw rows for the assembler; {@code quoteResponse.result} is an array, absent symbols are omitted. */
    @RequestLine("GET /v7/finance/quote?symbols={symbols}&formatted={formatted}")
    JsonNode quoteRows(@Param("symbols") String symbols, @Param("formatted") boolean formatted);
}
