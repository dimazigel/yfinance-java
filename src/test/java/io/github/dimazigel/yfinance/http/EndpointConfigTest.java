package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(config.adaptiveRateLimit().maxDelay()).isEqualTo(Duration.ofSeconds(30));
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
}
