package io.github.dimazigel.yfinance.internal.api;

import feign.Headers;
import feign.RequestLine;
import io.github.dimazigel.yfinance.internal.dto.news.NewsRequest;
import io.github.dimazigel.yfinance.internal.dto.news.NewsResponse;

/**
 * Feign binding for Yahoo's news gateway ({@code EndpointConfig.newsGatewayBase}), a GraphQL
 * endpoint at the host root. The {@code x-yahoo-cg-client-name} header is what authorises the
 * request: without it the gateway answers 401 whatever cookies are sent (probed 2026-10-13).
 */
public interface NewsApi {

    /** The header the gateway requires; its value names the Yahoo property asking. */
    String CLIENT_NAME_HEADER = "x-yahoo-cg-client-name";

    @RequestLine("POST /")
    @Headers({"Content-Type: application/json", CLIENT_NAME_HEADER + ": finance"})
    NewsResponse news(NewsRequest body);
}
