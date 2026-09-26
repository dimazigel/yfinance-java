package io.github.dimazigel.yfinance.http;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recovers from an expired/rotated crumb. When Yahoo answers an authenticated request with HTTP 401
 * or 403, this runs {@code onAuthFailure} (which should invalidate the cached crumb) and retries the
 * request exactly once, letting the downstream {@link CrumbInterceptor} attach a fresh crumb.
 *
 * <p>Must be installed <em>before</em> {@link CrumbInterceptor} so the retry re-runs crumb injection,
 * and before {@link AdaptiveRateLimitInterceptor} so the retry is paced and 429-handled as well.
 */
public final class AuthRetryInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AuthRetryInterceptor.class);

    private final Runnable onAuthFailure;

    public AuthRetryInterceptor(Runnable onAuthFailure) {
        this.onAuthFailure = onAuthFailure;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        var request = chain.request();
        Response response = chain.proceed(request);
        if (response.code() == 401 || response.code() == 403) {
            LOG.debug("HTTP {} from {}; refreshing crumb and retrying once",
                    response.code(), request.url().encodedPath());
            response.close();
            onAuthFailure.run();
            return chain.proceed(request);
        }
        return response;
    }
}
