package io.github.dimazigel.yfinance.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies adaptive client-side throttling based on observed HTTP 429 responses, and retries a
 * throttled request up to {@link AdaptiveRateLimitConfig#maxAttempts()} times in total, waiting the
 * adapted delay between attempts. The final response (429 or not) is returned to the caller.
 */
public final class AdaptiveRateLimitInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AdaptiveRateLimitInterceptor.class);

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
            if (response.code() != 429) {
                return response;
            }
            if (attempt >= maxAttempts) {
                if (maxAttempts > 1) {
                    LOG.atWarn()
                            .addKeyValue("status", 429)
                            .addKeyValue("attempts", attempt)
                            .addKeyValue("paceMs", limiter.currentDelay().toMillis())
                            .log("Giving up on {} after {} attempts: still rate limited (HTTP 429), pacing at {} ms",
                                    response.request().url().encodedPath(), attempt, limiter.currentDelay().toMillis());
                }
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
