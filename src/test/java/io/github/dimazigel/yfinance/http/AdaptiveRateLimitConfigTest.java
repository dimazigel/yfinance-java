package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AdaptiveRateLimitConfigTest {

    @Test
    void withersChangeOneFieldAndKeepTheRest() {   // batch B, item 5
        var defaults = AdaptiveRateLimitConfig.defaults();

        assertThat(defaults.withEnabled(false)).isEqualTo(new AdaptiveRateLimitConfig(
                false, Duration.ofMillis(500), Duration.ofSeconds(10), 2.0, 0.5, 0.2, 3));
        assertThat(defaults.withInitialDelay(Duration.ofMillis(250)).initialDelay()).isEqualTo(Duration.ofMillis(250));
        assertThat(defaults.withMaxDelay(Duration.ofSeconds(5)).maxDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.withBackoffMultiplier(3.0).backoffMultiplier()).isEqualTo(3.0);
        assertThat(defaults.withRecoveryFactor(0.25).recoveryFactor()).isEqualTo(0.25);
        assertThat(defaults.withJitterFactor(0.0).jitterFactor()).isEqualTo(0.0);
        assertThat(defaults.withMaxAttempts(5).maxAttempts()).isEqualTo(5);

        var chained = defaults.withMaxDelay(Duration.ofSeconds(5)).withMaxAttempts(1).withJitterFactor(0.1);
        assertThat(chained).isEqualTo(new AdaptiveRateLimitConfig(true, Duration.ofMillis(500), Duration.ofSeconds(5), 2.0, 0.5, 0.1, 1));
        assertThat(defaults).as("withers copy, never mutate").isEqualTo(AdaptiveRateLimitConfig.defaults());
    }

    @Test
    void withersStillValidate() {
        var defaults = AdaptiveRateLimitConfig.defaults();
        assertThatThrownBy(() -> defaults.withInitialDelay(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withMaxDelay(Duration.ofMillis(1))).as("below initialDelay").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withBackoffMultiplier(1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withRecoveryFactor(1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withJitterFactor(1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withMaxAttempts(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionalConstructorStillWorks() {   // 1.1.0 signature
        var config = new AdaptiveRateLimitConfig(true, Duration.ofMillis(1), Duration.ofMillis(5), 2.0, 0.5, 0.0, 3);
        assertThat(config.maxDelay()).isEqualTo(Duration.ofMillis(5));
    }
}
