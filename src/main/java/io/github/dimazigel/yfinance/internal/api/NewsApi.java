package io.github.dimazigel.yfinance.internal.api;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.internal.dto.news.NewsRequest;
import io.github.dimazigel.yfinance.internal.dto.news.NewsResponse;

/** Feign binding for the news stream endpoint of the finance.yahoo.com site (not a query host). */
public interface NewsApi {

    @RequestLine("POST /xhr/ncp?queryRef={queryRef}&serviceKey=ncp_fin")
    @Headers("Content-Type: application/json")
    NewsResponse news(@Param("queryRef") String queryRef, NewsRequest body);
}
