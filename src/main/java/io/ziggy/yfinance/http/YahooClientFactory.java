package io.ziggy.yfinance.http;

import io.ziggy.yfinance.auth.CrumbStore;
import io.ziggy.yfinance.valueobject.Crumb;
import java.util.function.Supplier;
import okhttp3.CookieJar;
import okhttp3.OkHttpClient;

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
        return new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .addInterceptor(new UserAgentInterceptor(config.userAgent()))
                .callTimeout(config.callTimeout())
                .build();
    }

    /**
     * Client used for authenticated data requests: shares the cookie jar with the auth client and
     * appends the crumb to every request. {@code crumb} may return {@code null} to send a request
     * without a crumb (e.g. while the crumb endpoint is rate-limited).
     */
    public static OkHttpClient apiClient(
            EndpointConfig config, CookieJar cookieJar, Supplier<Crumb> crumb, Runnable onAuthFailure) {
        return new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .addInterceptor(new UserAgentInterceptor(config.userAgent()))
                .addInterceptor(new AdaptiveRateLimitInterceptor(config.adaptiveRateLimit()))
                .addInterceptor(new AuthRetryInterceptor(onAuthFailure))
                .addInterceptor(new CrumbInterceptor(crumb))
                .callTimeout(config.callTimeout())
                .build();
    }

    /** Convenience builder wiring a fresh cookie jar, crumb store and api client together. */
    public static OkHttpClient apiClient(EndpointConfig config) {
        var cookieJar = new InMemoryCookieJar();
        var crumbStore = new CrumbStore(baseClient(config, cookieJar), config);
        return apiClient(config, cookieJar, () -> crumbStore.tryGetCrumb().orElse(null), crumbStore::invalidate);
    }
}
