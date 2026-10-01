/**
 * HTTP plumbing: the OkHttp client factory and its interceptors (user agent, log context, auth
 * retry, 5xx retry, adaptive rate limit, request log, crumb), the call budget, the Feign and
 * Jackson glue, and {@code RawQuoteClient}. The configuration records these read live in the
 * exported {@code http} package.
 *
 * <p><strong>Internal to the library — not API; may change without notice.</strong>
 */
@NullMarked
package io.github.dimazigel.yfinance.internal.http;

import org.jspecify.annotations.NullMarked;
