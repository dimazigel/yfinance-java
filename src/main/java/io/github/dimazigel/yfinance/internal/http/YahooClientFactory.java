package io.github.dimazigel.yfinance.internal.http;

import io.github.dimazigel.yfinance.http.AdaptiveRateLimitConfig;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.internal.auth.Crumb;
import io.github.dimazigel.yfinance.internal.auth.CrumbStore;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import okhttp3.ConnectionPool;
import okhttp3.CookieJar;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(YahooClientFactory.class);

    /** OkHttp's own default for {@code maxRequestsPerHost}; the dispatcher is never sized below it. */
    static final int MIN_REQUESTS_PER_HOST = 5;

    private YahooClientFactory() {}

    /**
     * The adaptive rate limiter shared by both clients. The pace cap is clamped to
     * {@link EndpointConfig#callTimeout()} when it is longer (a paced wait must fit inside the call
     * budget, so a longer cap could never be waited out); that clamp is logged once, at WARN,
     * because it means the configuration says one thing and the client does another.
     */
    public static AdaptiveRateLimitInterceptor newRateLimiter(EndpointConfig config) {
        AdaptiveRateLimitConfig tuning = config.adaptiveRateLimit();
        Duration callTimeout = config.callTimeout();
        if (tuning.enabled() && !callTimeout.isZero() && tuning.maxDelay().compareTo(callTimeout) > 0) {
            LOG.atWarn()
                    .addKeyValue("maxDelay", tuning.maxDelay())
                    .addKeyValue("callTimeout", callTimeout)
                    .log("rate-limit maxDelay {} clamped to callTimeout {}", tuning.maxDelay(), callTimeout);
            Duration initialDelay = tuning.initialDelay().compareTo(callTimeout) > 0 ? callTimeout : tuning.initialDelay();
            tuning = new AdaptiveRateLimitConfig(true, initialDelay, callTimeout, tuning.backoffMultiplier(),
                    tuning.recoveryFactor(), tuning.jitterFactor(), tuning.maxAttempts());
        }
        return new AdaptiveRateLimitInterceptor(tuning);
    }

    /**
     * The dispatcher shared by both clients, with {@code maxRequests} and {@code maxRequestsPerHost}
     * set to {@link EndpointConfig#fanOutConcurrency()} (never below OkHttp's default of
     * {@value #MIN_REQUESTS_PER_HOST}). OkHttp applies those limits only to {@code enqueue}d calls;
     * every call this library makes is synchronous and bounded by the fan-out semaphore alone, so
     * the sizing is for callers who {@code enqueue} through the customizer's client. A customizer
     * may replace the dispatcher (it runs last); the caller then owns that dispatcher.
     */
    public static Dispatcher newDispatcher(EndpointConfig config) {
        int inFlight = Math.max(config.fanOutConcurrency(), MIN_REQUESTS_PER_HOST);
        var dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(inFlight);
        dispatcher.setMaxRequestsPerHost(inFlight);
        return dispatcher;
    }

    /**
     * Client used for the auth handshake (cookie + crumb) with {@link EndpointConfig#cookieJar()}.
     * Carries the cookie jar and User-Agent but <em>not</em> the crumb interceptor, to avoid
     * recursion when fetching the crumb itself.
     */
    public static OkHttpClient baseClient(EndpointConfig config) {
        return baseClient(config, config.cookieJar());
    }

    /** The handshake client over an explicit {@code cookieJar}, with its own limiter, dispatcher and pool. */
    public static OkHttpClient baseClient(EndpointConfig config, CookieJar cookieJar) {
        return baseClient(config, cookieJar, newRateLimiter(config), newDispatcher(config), new ConnectionPool());
    }

    /**
     * The handshake client sharing {@code limiter}, {@code dispatcher} and {@code pool} with the api
     * client built from the same pieces. Chain: User-Agent → log context → adaptive rate limit →
     * request log; no crumb, no auth retry. When the handshake runs inside an api request that has
     * already waited for its slot, the shared limiter lets it through without a second wait (see
     * {@link AdaptiveRateLimitInterceptor}).
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
                newRateLimiter(config), newDispatcher(config), new ConnectionPool());
    }

    /**
     * Client used for authenticated data requests: shares the cookie jar, {@code limiter},
     * {@code dispatcher} and {@code pool} with the handshake client and appends the crumb to every
     * request. {@code onAuthFailure} receives the crumb Yahoo rejected with 401/403 ({@code null}
     * when the request carried none); see {@link AuthRetryInterceptor#onRejectedCrumb(Consumer, Supplier)}.
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
                .addInterceptor(AuthRetryInterceptor.onRejectedCrumb(onAuthFailure, crumb))
                .addInterceptor(new TransientErrorRetryInterceptor(config.transientRetry()))
                .addInterceptor(limiter)
                .addInterceptor(new RequestLogInterceptor())
                .addInterceptor(new CrumbInterceptor(crumb))
                .callTimeout(config.callTimeout());
        config.clientCustomizer().accept(builder);
        return builder.build();
    }

    /** Convenience builder wiring the config's cookie jar, a crumb store and an api client together. */
    public static OkHttpClient apiClient(EndpointConfig config) {
        var cookieJar = config.cookieJar();
        var limiter = newRateLimiter(config);
        var dispatcher = newDispatcher(config);
        var pool = new ConnectionPool();
        var crumbStore = new CrumbStore(baseClient(config, cookieJar, limiter, dispatcher, pool), config);
        return apiClient(config, cookieJar, () -> crumbStore.tryGetCrumb().orElse(null), crumbStore::invalidate,
                limiter, dispatcher, pool);
    }
}
