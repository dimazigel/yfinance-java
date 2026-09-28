package io.github.dimazigel.yfinance.http;

import io.github.dimazigel.yfinance.auth.CrumbStore;
import io.github.dimazigel.yfinance.valueobject.Crumb;
import java.util.function.Consumer;
import java.util.function.Supplier;
import okhttp3.ConnectionPool;
import okhttp3.CookieJar;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;

/**
 * Builds the OkHttp clients used to talk to Yahoo Finance. A {@code YFinance} instance owns two: the
 * handshake ({@linkplain #baseClient(EndpointConfig, CookieJar, AdaptiveRateLimitInterceptor,
 * Dispatcher, ConnectionPool) base}) client and the {@linkplain #apiClient(EndpointConfig,
 * CookieJar, Supplier, Consumer, AdaptiveRateLimitInterceptor, Dispatcher, ConnectionPool) api}
 * client. They share the cookie jar, the adaptive rate limiter (so the handshake is paced like any
 * data request while Yahoo is rate-limiting), the dispatcher and the connection pool. The shorter
 * overloads build those shared pieces privately and exist for tests and single-client use.
 */
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
        return baseClient(config, cookieJar, new AdaptiveRateLimitInterceptor(config.adaptiveRateLimit()),
                new Dispatcher(), new ConnectionPool());
    }

    /**
     * The handshake client sharing {@code limiter}, {@code dispatcher} and {@code pool} with the api
     * client built from the same pieces. Chain: User-Agent → log context → adaptive rate limit →
     * request log; no crumb, no auth retry.
     */
    public static OkHttpClient baseClient(
            EndpointConfig config,
            CookieJar cookieJar,
            AdaptiveRateLimitInterceptor limiter,
            Dispatcher dispatcher,
            ConnectionPool pool) {
        var builder = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(pool)
                .cookieJar(cookieJar)
                .addInterceptor(new UserAgentInterceptor(config.userAgent()))
                .addInterceptor(new LogContextInterceptor())
                .addInterceptor(limiter)
                .addInterceptor(new RequestLogInterceptor())
                .callTimeout(config.callTimeout());
        config.clientCustomizer().accept(builder);
        return builder.build();
    }

    /**
     * Client used for authenticated data requests: shares the cookie jar with the auth client and
     * appends the crumb to every request. {@code onAuthFailure} is run without the rejected crumb
     * (see the {@link Consumer} overload for the identity-aware form).
     */
    public static OkHttpClient apiClient(
            EndpointConfig config, CookieJar cookieJar, Supplier<@Nullable Crumb> crumb, Runnable onAuthFailure) {
        return apiClient(config, cookieJar, crumb, rejected -> onAuthFailure.run(),
                new AdaptiveRateLimitInterceptor(config.adaptiveRateLimit()), new Dispatcher(), new ConnectionPool());
    }

    /**
     * Client used for authenticated data requests: shares the cookie jar, {@code limiter},
     * {@code dispatcher} and {@code pool} with the handshake client and appends the crumb to every
     * request. {@code onAuthFailure} receives the crumb Yahoo rejected with 401/403 ({@code null}
     * when the request carried none); see {@link AuthRetryInterceptor}.
     *
     * <p>Interceptor order matters: {@link LogContextInterceptor} comes first so every line below
     * carries the endpoint; {@link AuthRetryInterceptor} and
     * {@link TransientErrorRetryInterceptor} sit upstream of {@link AdaptiveRateLimitInterceptor}
     * so every request they re-issue is paced and 429-handled like any other, and all three sit
     * upstream of {@link CrumbInterceptor} so every (re)issued request gets the current crumb.
     * {@link RequestLogInterceptor} sits just above the crumb so it logs every physical attempt
     * without ever seeing the credential. {@code crumb} may return {@code null} to send a request
     * without a crumb (e.g. while the crumb endpoint is rate-limited).
     */
    public static OkHttpClient apiClient(
            EndpointConfig config,
            CookieJar cookieJar,
            Supplier<@Nullable Crumb> crumb,
            Consumer<@Nullable String> onAuthFailure,
            AdaptiveRateLimitInterceptor limiter,
            Dispatcher dispatcher,
            ConnectionPool pool) {
        var builder = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(pool)
                .cookieJar(cookieJar)
                .addInterceptor(new UserAgentInterceptor(config.userAgent()))
                .addInterceptor(new LogContextInterceptor())
                .addInterceptor(new AuthRetryInterceptor(onAuthFailure))
                .addInterceptor(new TransientErrorRetryInterceptor(config.transientRetry()))
                .addInterceptor(limiter)
                .addInterceptor(new RequestLogInterceptor())
                .addInterceptor(new CrumbInterceptor(crumb))
                .callTimeout(config.callTimeout());
        config.clientCustomizer().accept(builder);
        return builder.build();
    }

    /** Convenience builder wiring a fresh cookie jar, crumb store and api client together. */
    public static OkHttpClient apiClient(EndpointConfig config) {
        var cookieJar = new InMemoryCookieJar();
        var limiter = new AdaptiveRateLimitInterceptor(config.adaptiveRateLimit());
        var dispatcher = new Dispatcher();
        var pool = new ConnectionPool();
        var crumbStore = new CrumbStore(baseClient(config, cookieJar, limiter, dispatcher, pool), config);
        return apiClient(config, cookieJar, () -> crumbStore.tryGetCrumb().orElse(null), crumbStore::invalidate,
                limiter, dispatcher, pool);
    }
}
