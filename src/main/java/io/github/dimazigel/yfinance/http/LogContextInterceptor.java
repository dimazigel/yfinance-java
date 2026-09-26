package io.github.dimazigel.yfinance.http;

import io.github.dimazigel.yfinance.logging.LogContext;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Puts the request path into the {@link LogContext#ENDPOINT} MDC key for the duration of the call,
 * so every log line from the interceptors below it (auth retry, 5xx retry, rate limiter) says which
 * Yahoo endpoint it is about. Restores the previous value afterwards, even on failure. Must be the
 * first interceptor after {@link UserAgentInterceptor}.
 */
public final class LogContextInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        try (var ignored = LogContext.endpoint(chain.request().url().encodedPath())) {
            return chain.proceed(chain.request());
        }
    }
}
