/**
 * HTTP layer. The API here is the configuration: {@link
 * io.github.dimazigel.yfinance.http.EndpointConfig}, {@link
 * io.github.dimazigel.yfinance.http.AdaptiveRateLimitConfig}, {@link
 * io.github.dimazigel.yfinance.http.RetryConfig} and the default {@link
 * io.github.dimazigel.yfinance.http.InMemoryCookieJar}. Everything else in the package — the
 * client factory, the interceptors, the Feign and Jackson glue, {@code RawQuoteClient}, the call
 * budget — is <strong>internal to the library: not API; may change without notice</strong>. It is
 * {@code public} only because the services live in another package, and it is left out of the
 * published Javadoc.
 */
@NullMarked
package io.github.dimazigel.yfinance.http;

import org.jspecify.annotations.NullMarked;
