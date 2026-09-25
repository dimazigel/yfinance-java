package io.github.dimazigel.yfinance.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Applies adaptive client-side throttling based on observed HTTP 429 responses, and retries a
 * throttled request up to {@link AdaptiveRateLimitConfig#maxAttempts()} times in total, waiting the
 * adapted delay between attempts. The final response (429 or not) is returned to the caller.
 */
public final class AdaptiveRateLimitInterceptor implements Interceptor {

    private final AdaptiveRateLimiter limiter;

    public AdaptiveRateLimitInterceptor(AdaptiveRateLimitConfig config) {
        this(new AdaptiveRateLimiter(config));
    }

    AdaptiveRateLimitInterceptor(AdaptiveRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        int maxAttempts = limiter.maxAttemptsPerRequest();
        for (int attempt = 1; ; attempt++) {
            awaitPermission();
            Response response = chain.proceed(chain.request());
            limiter.onResponse(response.code(), response.header("Retry-After"));
            if (response.code() != 429 || attempt >= maxAttempts) {
                return response;
            }
            response.close();
        }
    }

    private void awaitPermission() throws InterruptedIOException {
        try {
            limiter.beforeRequest();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for adaptive rate limit");
        }
    }
}
