package io.github.dimazigel.yfinance.http;

import io.github.dimazigel.yfinance.Tickers;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * Host roots and HTTP identity used to reach Yahoo Finance. Start from {@link #production()} and
 * derive variants with the {@code with...} methods; the canonical constructor exists for callers
 * who want to spell out everything. Note that two {@code production()} configs are never
 * {@code equals}: each holds its own cookie jar.
 *
 * @param query1Base  primary API host ({@code https://query1.finance.yahoo.com/})
 * @param query2Base  secondary API host ({@code https://query2.finance.yahoo.com/}), used for
 *     fundamentals timeseries
 * @param cookieUrl   URL hit purely to seed session cookies ({@code https://fc.yahoo.com/})
 * @param userAgent   the {@code User-Agent} header sent on every request
 * @param callTimeout overall per-call timeout applied to the OkHttp clients; it bounds the whole
 *     call, rate-limit pacing and retry backoffs included (a wait that would not fit in what is
 *     left of it fails at once). A {@code adaptiveRateLimit.maxDelay()} longer than it is clamped
 *     to it when the client is built ({@code YahooClientFactory.newRateLimiter}, one WARN);
 *     {@link Duration#ZERO} means no call timeout
 * @param adaptiveRateLimit adaptive client-side throttling after HTTP 429 responses
 * @param transientRetry retry policy for transient server errors (HTTP 500/502/503/504)
 * @param clientCustomizer hook applied to every OkHttp client builder <em>after</em> the library's
 *     own interceptors and timeouts, so it can add a proxy, extra interceptors (logging, metrics), a
 *     custom dispatcher or connection pool, or override a timeout; a dispatcher or pool it installs
 *     is the caller's to shut down — {@code YFinance.close()} releases only the ones the library
 *     created
 * @param fanOutConcurrency how many per-symbol requests a fan-out keeps in flight at once: the
 *     bound for {@code equityDetails(...)} and the other detail batches, and the default for
 *     {@code Tickers} (overridable per instance with {@code withConcurrency(n)}); at least 1,
 *     {@link Tickers#DEFAULT_CONCURRENCY} by default. The library's own calls are synchronous, so
 *     this semaphore is their only bound; the shared OkHttp dispatcher is sized to the same value
 *     for callers who {@code enqueue} through the customizer's client
 * @param cookieJar where the OkHttp clients store and send cookies (Yahoo's {@code A1}/{@code A3}
 *     session cookies); a fresh {@link InMemoryCookieJar} per {@link #production()} call. Must be
 *     thread-safe: both clients use it, and every {@code YFinance} created from the same
 *     {@code EndpointConfig} instance shares it. Supply a persistent jar with
 *     {@link #withCookieJar(CookieJar)} to survive restarts without a new handshake
 * @param clock the source of "now" for the services — {@code fetchedAt} on instruments and details,
 *     the end of an open-ended history window, the timeseries {@code period2};
 *     {@link Clock#systemUTC()} by default, fixable with {@link #withClock(Clock)} for as-of tests
 */
public record EndpointConfig(
        HttpUrl query1Base,
        HttpUrl query2Base,
        HttpUrl cookieUrl,
        String userAgent,
        Duration callTimeout,
        AdaptiveRateLimitConfig adaptiveRateLimit,
        RetryConfig transientRetry,
        Consumer<OkHttpClient.Builder> clientCustomizer,
        int fanOutConcurrency,
        CookieJar cookieJar,
        Clock clock) {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(30);

    private static final Consumer<OkHttpClient.Builder> NO_CUSTOMIZATION = EndpointConfig::noCustomization;

    public EndpointConfig {
        Objects.requireNonNull(query1Base, "query1Base");
        Objects.requireNonNull(query2Base, "query2Base");
        Objects.requireNonNull(cookieUrl, "cookieUrl");
        Objects.requireNonNull(userAgent, "userAgent");
        Objects.requireNonNull(callTimeout, "callTimeout");
        Objects.requireNonNull(adaptiveRateLimit, "adaptiveRateLimit");
        Objects.requireNonNull(transientRetry, "transientRetry");
        Objects.requireNonNull(clientCustomizer, "clientCustomizer");
        Objects.requireNonNull(cookieJar, "cookieJar");
        Objects.requireNonNull(clock, "clock");
        if (fanOutConcurrency < 1) {
            throw new IllegalArgumentException("fanOutConcurrency must be >= 1, was " + fanOutConcurrency);
        }
    }

    /**
     * The canonical constructor of 1.1.0: a fresh {@link InMemoryCookieJar} and
     * {@link Clock#systemUTC()} for the two components added since.
     *
     * @param query1Base primary API host
     * @param query2Base secondary API host
     * @param cookieUrl cookie-seeding URL
     * @param userAgent the {@code User-Agent} header
     * @param callTimeout overall per-call timeout
     * @param adaptiveRateLimit adaptive throttling after HTTP 429
     * @param transientRetry retry policy for 5xx
     * @param clientCustomizer hook applied last to every OkHttp client builder
     * @param fanOutConcurrency the fan-out bound, at least 1
     */
    public EndpointConfig(
            HttpUrl query1Base,
            HttpUrl query2Base,
            HttpUrl cookieUrl,
            String userAgent,
            Duration callTimeout,
            AdaptiveRateLimitConfig adaptiveRateLimit,
            RetryConfig transientRetry,
            Consumer<OkHttpClient.Builder> clientCustomizer,
            int fanOutConcurrency) {
        this(query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, transientRetry,
                clientCustomizer, fanOutConcurrency, new InMemoryCookieJar(), Clock.systemUTC());
    }

    /** The production Yahoo Finance configuration. */
    public static EndpointConfig production() {
        return new EndpointConfig(
                HttpUrl.get("https://query1.finance.yahoo.com/"),
                HttpUrl.get("https://query2.finance.yahoo.com/"),
                HttpUrl.get("https://fc.yahoo.com/"),
                DEFAULT_USER_AGENT,
                DEFAULT_CALL_TIMEOUT,
                AdaptiveRateLimitConfig.defaults(),
                RetryConfig.defaults(),
                NO_CUSTOMIZATION,
                Tickers.DEFAULT_CONCURRENCY,
                new InMemoryCookieJar(),
                Clock.systemUTC());
    }

    /**
     * Returns a copy with all three hosts pointed at {@code base}: handy for tests against a mock
     * server or for routing everything through one proxy front.
     */
    public EndpointConfig withHosts(HttpUrl base) {
        return withHosts(base, base, base);
    }

    /** Returns a copy with different hosts. */
    public EndpointConfig withHosts(HttpUrl query1Base, HttpUrl query2Base, HttpUrl cookieUrl) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, transientRetry,
                clientCustomizer, fanOutConcurrency, cookieJar, clock);
    }

    /** Returns a copy with a different {@code User-Agent}. */
    public EndpointConfig withUserAgent(String userAgent) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, transientRetry,
                clientCustomizer, fanOutConcurrency, cookieJar, clock);
    }

    /**
     * Returns a copy with a different call timeout. It bounds the whole call, paced waits included;
     * a {@code adaptiveRateLimit().maxDelay()} longer than it (10 s by default) is clamped to it
     * when the client is built, with one WARN — lower {@code maxDelay} explicitly to silence that.
     */
    public EndpointConfig withCallTimeout(Duration timeout) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, timeout, adaptiveRateLimit, transientRetry,
                clientCustomizer, fanOutConcurrency, cookieJar, clock);
    }

    /** Returns a copy with a different adaptive rate-limit config. */
    public EndpointConfig withAdaptiveRateLimit(AdaptiveRateLimitConfig config) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, config, transientRetry, clientCustomizer,
                fanOutConcurrency, cookieJar, clock);
    }

    /** Returns a copy with a different transient-server-error retry policy. */
    public EndpointConfig withTransientRetry(RetryConfig config) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, config, clientCustomizer,
                fanOutConcurrency, cookieJar, clock);
    }

    /**
     * Returns a copy whose OkHttp clients are passed through {@code customizer} last, e.g.
     * {@code withClientCustomizer(b -> b.proxy(myProxy))}.
     */
    public EndpointConfig withClientCustomizer(Consumer<OkHttpClient.Builder> customizer) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, transientRetry,
                customizer, fanOutConcurrency, cookieJar, clock);
    }

    /**
     * Returns a copy with a different fan-out bound: how many per-symbol requests the detail
     * batches keep in flight at once, and the default {@code Tickers} concurrency. Raising it
     * makes a batch of 500 equity details faster at the cost of more simultaneous requests
     * against Yahoo's rate limit; the adaptive limiter still paces every request while degraded.
     */
    public EndpointConfig withFanOutConcurrency(int concurrency) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, transientRetry,
                clientCustomizer, concurrency, cookieJar, clock);
    }

    /**
     * Returns a copy whose OkHttp clients store and send cookies through {@code cookieJar} instead
     * of a private {@link InMemoryCookieJar}: e.g. a persistent jar that keeps Yahoo's session
     * cookies ({@code A1}/{@code A3}) across restarts. The jar must be thread-safe, and every
     * {@code YFinance} created from a config holding the same instance shares it.
     */
    public EndpointConfig withCookieJar(CookieJar cookieJar) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, transientRetry,
                clientCustomizer, fanOutConcurrency, cookieJar, clock);
    }

    /**
     * Returns a copy whose services read "now" from {@code clock}: it stamps {@code fetchedAt} on
     * instruments and details, closes an open-ended history window and sets the timeseries
     * {@code period2}. {@link Clock#systemUTC()} by default; fix it for as-of tests.
     */
    public EndpointConfig withClock(Clock clock) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, transientRetry,
                clientCustomizer, fanOutConcurrency, cookieJar, clock);
    }

    private static void noCustomization(OkHttpClient.Builder builder) {}

    /** Whether a {@link #clientCustomizer()} other than the default no-op has been configured. */
    public boolean hasClientCustomizer() {
        // The default is a single shared method reference, so equals (identity for lambdas) is exact.
        return !NO_CUSTOMIZATION.equals(clientCustomizer);
    }

    /** URL of the crumb-issuing endpoint on the primary host. */
    public HttpUrl crumbUrl() {
        return query1Base.newBuilder().addPathSegments("v1/test/getcrumb").build();
    }
}
