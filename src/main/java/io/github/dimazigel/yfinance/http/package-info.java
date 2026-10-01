/**
 * Configuration of the HTTP layer: {@link io.github.dimazigel.yfinance.http.EndpointConfig},
 * {@link io.github.dimazigel.yfinance.http.AdaptiveRateLimitConfig},
 * {@link io.github.dimazigel.yfinance.http.RetryConfig} and the default
 * {@link io.github.dimazigel.yfinance.http.InMemoryCookieJar}. The client factory, interceptors
 * and Feign/Jackson glue that read it are internal and not exported.
 */
@NullMarked
package io.github.dimazigel.yfinance.http;

import org.jspecify.annotations.NullMarked;
