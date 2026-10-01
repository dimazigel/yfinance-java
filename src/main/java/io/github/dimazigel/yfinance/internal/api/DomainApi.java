package io.github.dimazigel.yfinance.internal.api;

import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.internal.dto.domain.DomainResponse;

/** Feign binding for Yahoo's sector and industry endpoints. */
public interface DomainApi {

    @RequestLine("GET /v1/finance/sectors/{key}?formatted=false&withReturns=true&lang=en-US&region=US")
    DomainResponse sector(@Param("key") String key);

    @RequestLine("GET /v1/finance/industries/{key}?formatted=false&withReturns=true&lang=en-US&region=US")
    DomainResponse industry(@Param("key") String key);
}
