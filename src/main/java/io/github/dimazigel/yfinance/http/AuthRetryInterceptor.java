package io.github.dimazigel.yfinance.http;

import java.io.IOException;
import java.util.function.Consumer;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recovers from an expired/rotated crumb. When Yahoo answers an authenticated request with HTTP 401
 * or 403, this hands the rejected crumb (the {@code crumb} query parameter of the request that was
 * actually sent, {@code null} when it carried none) to {@code onAuthFailure} — which should
 * invalidate the cached crumb <em>if it is still that one</em> — and retries the request exactly
 * once, letting the downstream {@link CrumbInterceptor} attach a fresh crumb. Passing the rejected
 * crumb is what keeps a burst of concurrent 401s down to one handshake: the workers after the
 * first find the cache already refreshed and leave it alone.
 *
 * <p>Must be installed <em>before</em> {@link CrumbInterceptor} so the retry re-runs crumb injection,
 * and before {@link AdaptiveRateLimitInterceptor} so the retry is paced and 429-handled as well.
 */
public final class AuthRetryInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AuthRetryInterceptor.class);

    private final Consumer<@Nullable String> onAuthFailure;

    /** {@code onAuthFailure} receives the rejected crumb; see the class comment. */
    public AuthRetryInterceptor(Consumer<@Nullable String> onAuthFailure) {
        this.onAuthFailure = onAuthFailure;
    }

    /** Hook without the rejected crumb (unconditional invalidation); kept for existing callers. */
    public AuthRetryInterceptor(Runnable onAuthFailure) {
        this(rejected -> onAuthFailure.run());
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
            response.close();
            onAuthFailure.accept(rejected);
            return chain.proceed(request);
        }
        return response;
    }
}
