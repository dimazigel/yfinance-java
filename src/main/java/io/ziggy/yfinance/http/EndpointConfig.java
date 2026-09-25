package io.ziggy.yfinance.http;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * Host roots and HTTP identity used to reach Yahoo Finance. Start from {@link #production()} and
 * derive variants with the {@code with...} methods; the canonical constructor exists for callers
 * who want to spell out everything.
 *
 * @param query1Base  primary API host ({@code https://query1.finance.yahoo.com/})
 * @param query2Base  secondary API host ({@code https://query2.finance.yahoo.com/}), used for
 *     fundamentals timeseries
 * @param cookieUrl   URL hit purely to seed session cookies ({@code https://fc.yahoo.com/})
 * @param userAgent   the {@code User-Agent} header sent on every request
 * @param callTimeout overall per-call timeout applied to the OkHttp clients
 * @param adaptiveRateLimit adaptive client-side throttling after HTTP 429 responses
 * @param clientCustomizer hook applied to every OkHttp client builder <em>after</em> the library's
 *     own interceptors and timeouts, so it can add a proxy, extra interceptors (logging, metrics), a
 *     custom dispatcher or connection pool, or override a timeout
 */
public record EndpointConfig(
        HttpUrl query1Base,
        HttpUrl query2Base,
        HttpUrl cookieUrl,
        String userAgent,
        Duration callTimeout,
        AdaptiveRateLimitConfig adaptiveRateLimit,
        Consumer<OkHttpClient.Builder> clientCustomizer) {

    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(30);

    private static final Consumer<OkHttpClient.Builder> NO_CUSTOMIZATION = builder -> {};

    public EndpointConfig {
        Objects.requireNonNull(query1Base, "query1Base");
        Objects.requireNonNull(query2Base, "query2Base");
        Objects.requireNonNull(cookieUrl, "cookieUrl");
        Objects.requireNonNull(userAgent, "userAgent");
        Objects.requireNonNull(callTimeout, "callTimeout");
        Objects.requireNonNull(adaptiveRateLimit, "adaptiveRateLimit");
        Objects.requireNonNull(clientCustomizer, "clientCustomizer");
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
                NO_CUSTOMIZATION);
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
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, clientCustomizer);
    }

    /** Returns a copy with a different {@code User-Agent}. */
    public EndpointConfig withUserAgent(String userAgent) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, clientCustomizer);
    }

    /** Returns a copy with a different call timeout. */
    public EndpointConfig withCallTimeout(Duration timeout) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, timeout, adaptiveRateLimit, clientCustomizer);
    }

    /** Returns a copy with a different adaptive rate-limit config. */
    public EndpointConfig withAdaptiveRateLimit(AdaptiveRateLimitConfig config) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, config, clientCustomizer);
    }

    /**
     * Returns a copy whose OkHttp clients are passed through {@code customizer} last, e.g.
     * {@code withClientCustomizer(b -> b.proxy(myProxy))}.
     */
    public EndpointConfig withClientCustomizer(Consumer<OkHttpClient.Builder> customizer) {
        return new EndpointConfig(
                query1Base, query2Base, cookieUrl, userAgent, callTimeout, adaptiveRateLimit, customizer);
    }

    /** URL of the crumb-issuing endpoint on the primary host. */
    public HttpUrl crumbUrl() {
        return query1Base.newBuilder().addPathSegments("v1/test/getcrumb").build();
    }
}
