package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.Tickers;
import java.time.Duration;
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
        assertThat(config.cookieUrl()).isEqualTo(URL);
        assertThat(config.crumbUrl()).isEqualTo(URL.resolve("v1/test/getcrumb"));
        assertThat(config.userAgent()).isEqualTo(EndpointConfig.production().userAgent()); // untouched
    }

    @Test
    void withHostsCanSetEachHostSeparately() {
        var other = HttpUrl.get("https://other.test/");
        var config = EndpointConfig.production().withHosts(URL, other, URL);
        assertThat(config.query1Base()).isEqualTo(URL);
        assertThat(config.query2Base()).isEqualTo(other);
        assertThat(config.cookieUrl()).isEqualTo(URL);
    }

    @Test
    void withMethodsEachChangeOneFieldAndKeepTheRest() {
        var custom = EndpointConfig.production()
                .withUserAgent("ua/1")
                .withAdaptiveRateLimit(AdaptiveRateLimitConfig.disabled())
                .withCallTimeout(Duration.ofSeconds(5))
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
        assertThat(none.withCallTimeout(Duration.ofSeconds(15)).transientRetry()).isEqualTo(RetryConfig.disabled());
    }

    @Test
    void fanOutConcurrencyDefaultsToTheTickersDefaultAndIsValidated() {   // final review, finding 5
        var production = EndpointConfig.production();
        assertThat(production.fanOutConcurrency()).isEqualTo(Tickers.DEFAULT_CONCURRENCY).isEqualTo(4);

        var eight = production.withFanOutConcurrency(8);
        assertThat(eight.fanOutConcurrency()).isEqualTo(8);
        assertThat(eight.callTimeout()).isEqualTo(production.callTimeout());
        assertThat(eight.withCallTimeout(Duration.ofSeconds(15)).fanOutConcurrency()).as("copies keep it").isEqualTo(8);
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
    void maxDelayMustBeShorterThanTheCallTimeout() {
        // callTimeout bounds the whole call, pacing included: a maxDelay that does not fit could
        // never be waited out, so the combination is rejected up front.
        var production = EndpointConfig.production();

        assertThatThrownBy(() -> production.withCallTimeout(Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDelay").hasMessageContaining("PT10S")
                .hasMessageContaining("callTimeout").hasMessageContaining("PT10S");
        assertThatThrownBy(() -> production.withAdaptiveRateLimit(
                new AdaptiveRateLimitConfig(true, Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0, 0.5, 0.0, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PT30S").hasMessageContaining("PT30S");

        assertThat(production.withCallTimeout(Duration.ofSeconds(11)).callTimeout()).isEqualTo(Duration.ofSeconds(11));
        assertThat(production.withAdaptiveRateLimit(AdaptiveRateLimitConfig.disabled()).withCallTimeout(Duration.ofSeconds(1))
                .callTimeout()).as("a disabled limiter never paces").isEqualTo(Duration.ofSeconds(1));
        assertThat(production.withCallTimeout(Duration.ZERO).callTimeout()).as("zero = no call timeout in OkHttp").isZero();
    }
}
