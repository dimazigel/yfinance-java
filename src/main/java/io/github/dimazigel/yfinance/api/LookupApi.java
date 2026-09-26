package io.github.dimazigel.yfinance.api;

import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.dto.lookup.LookupResponse;

/** Feign binding for Yahoo's lookup endpoint. */
public interface LookupApi {

    @RequestLine("GET /v1/finance/lookup?query={query}&type={type}&start={start}&count={count}"
            + "&formatted={formatted}&fetchPricingData={fetchPricingData}")
    LookupResponse lookup(
            @Param("query") String query,
            @Param("type") String type,
            @Param("start") int start,
            @Param("count") int count,
            @Param("formatted") boolean formatted,
            @Param("fetchPricingData") boolean fetchPricingData);
}
