package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.Tickers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

class EndpointConfigTest {

    private static final HttpUrl URL = HttpUrl.get("https://example.test/");

    @Test
    void productionHasDefaults() {
        var config = EndpointConfig.production();
        assertThat(config.callTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.query1Base().host()).isEqualTo("query1.finance.yahoo.com");
        assertThat(config.query2Base().host()).isEqualTo("query2.finance.yahoo.com");
        assertThat(config.financeBase().host()).isEqualTo("finance.yahoo.com");
        assertThat(config.cookieUrl().host()).isEqualTo("fc.yahoo.com");
        assertThat(config.userAgent()).startsWith("Mozilla/5.0");
        assertThat(config.adaptiveRateLimit().enabled()).isTrue();
        assertThat(config.adaptiveRateLimit().maxDelay()).as("well under the 30 s call timeout").isEqualTo(Duration.ofSeconds(10));
        assertThat(config.clientCustomizer()).isNotNull();
    }

    @Test
    void withHostsPointsEverythingAtOneBase() {
        var config = EndpointConfig.production().withHosts(URL);
        assertThat(config.query1Base()).isEqualTo(URL);
        assertThat(config.query2Base()).isEqualTo(URL);
        assertThat(config.financeBase()).isEqualTo(URL);
        assertThat(config.cookieUrl()).isEqualTo(URL);
        assertThat(config.crumbUrl()).isEqualTo(URL.resolve("v1/test/getcrumb"));
        assertThat(config.userAgent()).isEqualTo(EndpointConfig.production().userAgent()); // untouched
    }

    @Test
    void withHostsCanSetEachHostSeparately() {
        var other = HttpUrl.get("https://other.test/");
        var site = HttpUrl.get("https://site.test/");
        var config = EndpointConfig.production().withHosts(URL, other, site, URL);
        assertThat(config.query1Base()).isEqualTo(URL);
        assertThat(config.query2Base()).isEqualTo(other);
        assertThat(config.financeBase()).isEqualTo(site);
        assertThat(config.cookieUrl()).isEqualTo(URL);
    }

    @Test
    void withMethodsEachChangeOneFieldAndKeepTheRest() {
        var custom = EndpointConfig.production()
                .withUserAgent("ua/1")
                .withCallTimeout(Duration.ofSeconds(5))
                .withAdaptiveRateLimit(AdaptiveRateLimitConfig.disabled())
                .withClientCustomizer(b -> b.followRedirects(false));

        assertThat(custom.userAgent()).isEqualTo("ua/1");
        assertThat(custom.callTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(custom.adaptiveRateLimit().enabled()).isFalse();
        assertThat(custom.query1Base()).isEqualTo(EndpointConfig.production().query1Base());

        var marked = custom.clientCustomizer();
        assertThat(custom.withCallTimeout(Duration.ofSeconds(1)).clientCustomizer()).isSameAs(marked);
        assertThat(custom.withUserAgent("x").clientCustomizer()).isSameAs(marked);
        assertThat(custom.withHosts(URL).adaptiveRateLimit().enabled()).isFalse();
    }

    @Test
    void transientRetryDefaultsAndCopies() {
        var config = EndpointConfig.production();
        assertThat(config.transientRetry()).isEqualTo(RetryConfig.defaults());

        var none = config.withTransientRetry(RetryConfig.disabled());
        assertThat(none.transientRetry().maxAttempts()).isEqualTo(1);
        assertThat(none.withCallTimeout(Duration.ofSeconds(1)).transientRetry()).isEqualTo(RetryConfig.disabled());
    }

    @Test
    void fanOutConcurrencyDefaultsToTheTickersDefaultAndIsValidated() {   // final review, finding 5
        var production = EndpointConfig.production();
        assertThat(production.fanOutConcurrency()).isEqualTo(Tickers.DEFAULT_CONCURRENCY).isEqualTo(4);

        var eight = production.withFanOutConcurrency(8);
        assertThat(eight.fanOutConcurrency()).isEqualTo(8);
        assertThat(eight.callTimeout()).isEqualTo(production.callTimeout());
        assertThat(eight.withCallTimeout(Duration.ofSeconds(1)).fanOutConcurrency()).as("copies keep it").isEqualTo(8);
        assertThat(eight.withHosts(URL).fanOutConcurrency()).isEqualTo(8);
        assertThat(eight).isEqualTo(production.withFanOutConcurrency(8));

        assertThatThrownBy(() -> production.withFanOutConcurrency(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsWhetherACustomizerIsConfigured() {
        assertThat(EndpointConfig.production().hasClientCustomizer()).isFalse();
        assertThat(EndpointConfig.production().withHosts(URL).hasClientCustomizer()).isFalse(); // default survives copies
        assertThat(EndpointConfig.production().withClientCustomizer(b -> {}).hasClientCustomizer()).isTrue();
    }

    @Test
    void anyCallTimeoutIsAcceptedInAnyWitherOrder() {   // review, important 1: the record never rejects a tuning
        var production = EndpointConfig.production();
        assertThat(production.withCallTimeout(Duration.ofSeconds(10)).callTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(production.withCallTimeout(Duration.ofSeconds(1)).adaptiveRateLimit().maxDelay())
                .as("a maxDelay longer than the call timeout is clamped when the client is built, not rejected here")
                .isEqualTo(Duration.ofSeconds(10));
        assertThat(production.withCallTimeout(Duration.ZERO).callTimeout()).isZero();
    }

    @Test
    void cookieJarAndClockHaveDefaultsAndWithers() {   // batch B, item 5
        var production = EndpointConfig.production();
        assertThat(production.cookieJar()).isInstanceOf(InMemoryCookieJar.class);
        assertThat(production.clock()).isEqualTo(Clock.systemUTC());
        assertThat(EndpointConfig.production().cookieJar()).as("each production() has its own jar").isNotSameAs(production.cookieJar());

        var jar = new InMemoryCookieJar();
        var fixed = Clock.fixed(Instant.ofEpochSecond(1_750_000_000L), ZoneOffset.UTC);
        var custom = production.withCookieJar(jar).withClock(fixed);
        assertThat(custom.cookieJar()).isSameAs(jar);
        assertThat(custom.clock()).isSameAs(fixed);
        assertThat(custom.callTimeout()).isEqualTo(production.callTimeout());

        assertThat(custom.withHosts(URL).cookieJar()).as("copies keep them").isSameAs(jar);
        assertThat(custom.withCallTimeout(Duration.ofSeconds(1)).clock()).isSameAs(fixed);
        assertThat(custom.withFanOutConcurrency(2).cookieJar()).isSameAs(jar);
        assertThat(custom.withUserAgent("x").clock()).isSameAs(fixed);
        assertThat(custom.withAdaptiveRateLimit(AdaptiveRateLimitConfig.disabled()).cookieJar()).isSameAs(jar);
        assertThat(custom.withTransientRetry(RetryConfig.disabled()).clock()).isSameAs(fixed);
        assertThat(custom.withClientCustomizer(b -> {}).cookieJar()).isSameAs(jar);
        assertThat(production.withCookieJar(jar).clock()).isEqualTo(Clock.systemUTC());
        assertThat(production.withClock(fixed).cookieJar()).isSameAs(production.cookieJar());
    }

}
