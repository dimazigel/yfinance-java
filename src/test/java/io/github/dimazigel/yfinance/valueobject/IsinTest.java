package io.github.dimazigel.yfinance.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IsinTest {

    @ParameterizedTest
    @ValueSource(strings = {"US0378331005", "FR0000120271", "KR7005930003", "GB00BH4HKS39", "US78462F1030", "DE000BAY0017"})
    void acceptsRealIsins(String isin) {
        assertThat(Isin.of(isin).value()).isEqualTo(isin);
    }

    @Test
    void isTrimmedAndUpperCased() {
        Isin apple = Isin.of(" us0378331005 ");

        assertThat(apple.value()).isEqualTo("US0378331005");
        assertThat(apple.countryCode()).isEqualTo("US");
        assertThat(apple).hasToString("US0378331005").isEqualTo(new Isin("US0378331005"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "US037833100", "US03783310055", "U10378331005", "US03783310-5", "US037833100X"})
    void rejectsAMalformedIsin(String isin) {
        assertThatThrownBy(() -> Isin.of(isin)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ISIN");
    }

    @Test
    void rejectsAWrongCheckDigit() {
        assertThatThrownBy(() -> Isin.of("US0378331006")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check digit")
                .hasMessageContaining("US0378331006");
    }
}
