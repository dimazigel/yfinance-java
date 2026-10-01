package io.github.dimazigel.yfinance.internal.http;

import io.github.dimazigel.yfinance.http.AdaptiveRateLimitConfig;
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
 * cannot fit in what is left of the budget fails immediately with {@link RateLimitBudgetExceeded}
 * instead of sleeping into a certain timeout. Waits run in slices of at most {@link CallBudget#SLICE}
 * so a cancelled call is noticed promptly. Each attempt reports when it was <em>sent</em> to the
 * limiter, which is how a burst of 429s for requests already in flight raises the pace once rather
 * than once per response.
 *
 * <p>One instance is shared by the api client and the handshake client. A handshake triggered from
 * inside an api request (the {@link CrumbInterceptor} below this one finds no crumb) runs on the
 * same thread, so it is recognised through a thread-local flag and its first attempt is sent without
 * waiting for another slot: the api request already paid for this one. Its responses still feed the
 * limiter, and its own 429 retries wait like any other request.
 */
public final class AdaptiveRateLimitInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AdaptiveRateLimitInterceptor.class);
    private static final ThreadLocal<Boolean> INSIDE_PACED_CALL = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final AdaptiveRateLimiter limiter;

    public AdaptiveRateLimitInterceptor(AdaptiveRateLimitConfig config) {
        this(new AdaptiveRateLimiter(config));
    }

    AdaptiveRateLimitInterceptor(AdaptiveRateLimiter limiter) {
        this.limiter = limiter;
    }

    /** The shared limiter state (tests and diagnostics). */
    AdaptiveRateLimiter limiter() {
        return limiter;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        long enteredNanos = limiter.nanoTime();
        int maxAttempts = limiter.maxAttemptsPerRequest();
        boolean nested = INSIDE_PACED_CALL.get();
        for (int attempt = 1; ; attempt++) {
            // A nested handshake's first attempt rides in the slot the enclosing api request already
            // waited for; its 429 retries are paced like any other request.
            if (!nested || attempt > 1) {
                awaitPermission(chain, enteredNanos);
            }
            long sentNanos = limiter.nanoTime();
            Response response = proceedPaced(chain, nested);
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

    /** Sends the request with the "inside a paced call" flag raised for anything nested on this thread. */
    private static Response proceedPaced(Chain chain, boolean alreadyNested) throws IOException {
        INSIDE_PACED_CALL.set(Boolean.TRUE);
        try {
            return chain.proceed(chain.request());
        } finally {
            if (alreadyNested) {
                INSIDE_PACED_CALL.set(Boolean.TRUE);
            } else {
                INSIDE_PACED_CALL.remove();
            }
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
            CallBudget.checkNotCanceled(chain);
            if (!CallBudget.fits(chain, enteredNanos, limiter.nanoTime(), wait)) {
                throw new RateLimitBudgetExceeded(wait);
            }
            try {
                limiter.sleep(CallBudget.slice(wait));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while waiting for adaptive rate limit");
            }
            wait = limiter.pendingWait();
        }
    }
}
