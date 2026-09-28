package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryConfigTest {

    @Test
    void withersChangeOneFieldAndKeepTheRest() {   // batch B, item 5
        var defaults = RetryConfig.defaults();

        assertThat(defaults.withMaxAttempts(5)).isEqualTo(new RetryConfig(5, Duration.ofMillis(500), Duration.ofSeconds(5)));
        assertThat(defaults.withInitialDelay(Duration.ofSeconds(1))).isEqualTo(new RetryConfig(3, Duration.ofSeconds(1), Duration.ofSeconds(5)));
        assertThat(defaults.withMaxDelay(Duration.ofSeconds(9))).isEqualTo(new RetryConfig(3, Duration.ofMillis(500), Duration.ofSeconds(9)));
        assertThat(defaults.withMaxAttempts(1).withMaxDelay(Duration.ofSeconds(2)))
                .isEqualTo(new RetryConfig(1, Duration.ofMillis(500), Duration.ofSeconds(2)));
        assertThat(defaults).isEqualTo(RetryConfig.defaults());
    }

    @Test
    void withersStillValidate() {
        var defaults = RetryConfig.defaults();
        assertThatThrownBy(() -> defaults.withMaxAttempts(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withInitialDelay(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withMaxDelay(Duration.ofMillis(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
