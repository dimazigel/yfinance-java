package io.github.dimazigel.yfinance.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SymbolTest {

    @Test
    void upperCasesAndTrims() {
        assertThat(Symbol.of("  aapl ").value()).isEqualTo("AAPL");
    }

    @Test
    void preservesYahooSpecialCharacters() {
        assertThat(Symbol.of("^gspc").value()).isEqualTo("^GSPC");
        assertThat(Symbol.of("brk-b").value()).isEqualTo("BRK-B");
        assertThat(Symbol.of("es=f").value()).isEqualTo("ES=F");
        assertThat(Symbol.of("bmw.de").value()).isEqualTo("BMW.DE");
    }

    @Test
    void toStringReturnsRawValue() {
        assertThat(Symbol.of("msft")).hasToString("MSFT");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> Symbol.of("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Symbol.of(null)).isInstanceOf(NullPointerException.class);
    }
}
