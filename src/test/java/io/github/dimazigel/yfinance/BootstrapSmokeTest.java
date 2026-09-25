package io.github.dimazigel.yfinance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BootstrapSmokeTest {

    @Test
    void buildAndTestInfraWorks() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }
}
