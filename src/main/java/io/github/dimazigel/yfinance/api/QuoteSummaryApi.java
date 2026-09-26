package io.github.dimazigel.yfinance.api;

import feign.Param;
import feign.RequestLine;
import tools.jackson.databind.JsonNode;

/** Feign binding for Yahoo's quoteSummary endpoint. */
public interface QuoteSummaryApi {

    /** Raw modules for the assembler; {@code quoteSummary.result[0]} maps module name to object. */
    @RequestLine("GET /v10/finance/quoteSummary/{symbol}?modules={modules}&formatted={formatted}&corsDomain={corsDomain}")
    JsonNode modules(@Param("symbol") String symbol, @Param("modules") String modules,
            @Param("formatted") boolean formatted, @Param("corsDomain") String corsDomain);
}
