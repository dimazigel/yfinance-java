package io.github.dimazigel.yfinance.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies adaptive client-side throttling based on observed HTTP 429 responses, and retries a
 * throttled request up to {@link AdaptiveRateLimitConfig#maxAttempts()} times in total, waiting the
 * adapted delay between attempts. The final response (429 or not) is returned to the caller.
 *
 * <p>Every wait is bounded by the call's {@code callTimeout} (see {@link CallBudget}): a wait that
 * cannot fit in what is left of the budget fails immediately instead of sleeping into a certain
 * timeout. Waits run in slices of at most {@value #SLICE_SECONDS} s so a cancelled call is noticed
 * promptly. Each attempt reports when it was <em>sent</em> to the limiter, which is how a burst of
 * 429s for requests already in flight raises the pace once rather than once per response.
 */
public final class AdaptiveRateLimitInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AdaptiveRateLimitInterceptor.class);
    private static final long SLICE_SECONDS = 1;
    private static final Duration SLICE = Duration.ofSeconds(SLICE_SECONDS);

    private final AdaptiveRateLimiter limiter;

    public AdaptiveRateLimitInterceptor(AdaptiveRateLimitConfig config) {
        this(new AdaptiveRateLimiter(config));
    }

    AdaptiveRateLimitInterceptor(AdaptiveRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        long enteredNanos = limiter.nanoTime();
        int maxAttempts = limiter.maxAttemptsPerRequest();
        for (int attempt = 1; ; attempt++) {
            awaitPermission(chain, enteredNanos);
            long sentNanos = limiter.nanoTime();
            Response response = chain.proceed(chain.request());
            limiter.onResponse(response.code(), response.header("Retry-After"), sentNanos);
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

    /** Waits until the limiter lets this request through, in slices, within the call's budget. */
    private void awaitPermission(Chain chain, long enteredNanos) throws InterruptedIOException {
        Duration wait = limiter.pendingWait();
        if (wait.isZero()) {
            return;
        }
        LOG.atDebug().addKeyValue("delayMs", wait.toMillis()).log("Rate limited; waiting {} ms before next request", wait.toMillis());
        while (!wait.isZero()) {
            if (chain.call().isCanceled()) {
                throw new InterruptedIOException("Canceled");
            }
            CallBudget.ensureFits(chain, enteredNanos, limiter.nanoTime(), wait, "rate-limit");
            try {
                limiter.sleep(wait.compareTo(SLICE) > 0 ? SLICE : wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while waiting for adaptive rate limit");
            }
            wait = limiter.pendingWait();
        }
    }
}
