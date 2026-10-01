package io.github.dimazigel.yfinance.internal.http;

import io.github.dimazigel.yfinance.internal.auth.Crumb;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recovers from an expired/rotated crumb. When Yahoo answers an authenticated request with HTTP 401
 * or 403, this runs the auth-failure hook (which should invalidate the cached crumb) and retries the
 * request exactly once, letting the downstream {@link CrumbInterceptor} attach a fresh crumb.
 *
 * <p>{@link #onRejectedCrumb} hands the hook the crumb Yahoo rejected — the {@code crumb} query
 * parameter of the request that was actually sent, {@code null} when it carried none — so the hook
 * can invalidate <em>only if the cache still holds that crumb</em>. That is what keeps a burst of
 * concurrent 401s down to one handshake: the workers after the first find the cache already
 * refreshed and leave it alone.
 *
 * <p>Must be installed <em>before</em> {@link CrumbInterceptor} so the retry re-runs crumb injection,
 * and before {@link AdaptiveRateLimitInterceptor} so the retry is paced and 429-handled as well.
 */
public final class AuthRetryInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AuthRetryInterceptor.class);

    private final Consumer<@Nullable String> onAuthFailure;
    private final @Nullable Supplier<@Nullable Crumb> crumb;

    private AuthRetryInterceptor(Consumer<@Nullable String> onAuthFailure, @Nullable Supplier<@Nullable Crumb> crumb) {
        this.onAuthFailure = onAuthFailure;
        this.crumb = crumb;
    }

    /** Identity-aware hook: receives the rejected crumb ({@code null} when the request carried none); always retries once. */
    public static AuthRetryInterceptor onRejectedCrumb(Consumer<@Nullable String> onAuthFailure) {
        return new AuthRetryInterceptor(onAuthFailure, null);
    }

    /**
     * As {@link #onRejectedCrumb(Consumer)}, and skips the retry when the rejected request carried no
     * crumb and {@code crumb} still yields none: a second crumbless round trip could only be rejected
     * again (the case for every request made during a crumb cooldown).
     */
    public static AuthRetryInterceptor onRejectedCrumb(Consumer<@Nullable String> onAuthFailure, Supplier<@Nullable Crumb> crumb) {
        return new AuthRetryInterceptor(onAuthFailure, crumb);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        var request = chain.request();
        Response response = chain.proceed(request);
        if (response.code() == 401 || response.code() == 403) {
            LOG.atDebug()
                    .addKeyValue("status", response.code())
                    .log("HTTP {} from Yahoo; refreshing crumb and retrying once", response.code());
            // response.request() is the request as sent, i.e. after CrumbInterceptor appended the crumb.
            String rejected = response.request().url().queryParameter("crumb");
            boolean retry;
            try {
                onAuthFailure.accept(rejected);
                // crumb.get() may throw (a rejected handshake rethrows from tryGetCrumb): the 401 must not leak.
                retry = rejected != null || crumb == null || crumb.get() != null;
            } catch (RuntimeException e) {
                response.close();
                throw e;
            }
            if (!retry) {
                LOG.atDebug().log("Request carried no crumb and none is available; not retrying");
                return response;
            }
            response.close();
            return chain.proceed(request);
        }
        return response;
    }
}
