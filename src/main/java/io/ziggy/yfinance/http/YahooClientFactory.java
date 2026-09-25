package io.ziggy.yfinance.http;

import io.ziggy.yfinance.auth.CrumbStore;
import io.ziggy.yfinance.valueobject.Crumb;
import java.util.function.Supplier;
import okhttp3.CookieJar;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;

/** Builds the OkHttp clients used to talk to Yahoo Finance. */
public final class YahooClientFactory {

    private YahooClientFactory() {}

    /**
     * Client used for the auth handshake (cookie + crumb). Carries the cookie jar and User-Agent
     * but <em>not</em> the crumb interceptor, to avoid recursion when fetching the crumb itself.
     */
    public static OkHttpClient baseClient(EndpointConfig config) {
        return baseClient(config, new InMemoryCookieJar());
    }

    public static OkHttpClient baseClient(EndpointConfig config, CookieJar cookieJar) {
        var builder = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .addInterceptor(new UserAgentInterceptor(config.userAgent()))
                .callTimeout(config.callTimeout());
        config.clientCustomizer().accept(builder);
        return builder.build();
    }

    /**
     * Client used for authenticated data requests: shares the cookie jar with the auth client and
     * appends the crumb to every request.
     *
     * <p>Interceptor order matters: {@link AuthRetryInterceptor} sits upstream of
     * {@link AdaptiveRateLimitInterceptor} so the request it re-issues after a crumb refresh is
     * paced and 429-handled like any other, and both sit upstream of {@link CrumbInterceptor} so
     * every (re)issued request gets the current crumb. {@code crumb} may return {@code null} to send a request
     * without a crumb (e.g. while the crumb endpoint is rate-limited).
     */
    public static OkHttpClient apiClient(
            EndpointConfig config, CookieJar cookieJar, Supplier<@Nullable Crumb> crumb, Runnable onAuthFailure) {
        var builder = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .addInterceptor(new UserAgentInterceptor(config.userAgent()))
                .addInterceptor(new AuthRetryInterceptor(onAuthFailure))
                .addInterceptor(new AdaptiveRateLimitInterceptor(config.adaptiveRateLimit()))
                .addInterceptor(new CrumbInterceptor(crumb))
                .callTimeout(config.callTimeout());
        config.clientCustomizer().accept(builder);
        return builder.build();
    }

    /** Convenience builder wiring a fresh cookie jar, crumb store and api client together. */
    public static OkHttpClient apiClient(EndpointConfig config) {
        var cookieJar = new InMemoryCookieJar();
        var crumbStore = new CrumbStore(baseClient(config, cookieJar), config);
        return apiClient(config, cookieJar, () -> crumbStore.tryGetCrumb().orElse(null), crumbStore::invalidate);
    }
}
