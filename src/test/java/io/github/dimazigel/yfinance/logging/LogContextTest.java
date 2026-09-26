package io.github.dimazigel.yfinance.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LogContextTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void keysAreStableAndPrefixed() {
        assertThat(LogContext.OP).isEqualTo("yf.op");
        assertThat(LogContext.SYMBOL).isEqualTo("yf.symbol");
        assertThat(LogContext.ENDPOINT).isEqualTo("yf.endpoint");
    }

    @Test
    void scopeSetsOpAndSymbolAndClearsThemOnClose() {
        try (var ignored = LogContext.scope("history", Symbol.of("AAPL"))) {
            assertThat(MDC.get(LogContext.OP)).isEqualTo("history");
            assertThat(MDC.get(LogContext.SYMBOL)).isEqualTo("AAPL");
        }
        assertThat(MDC.get(LogContext.OP)).isNull();
        assertThat(MDC.get(LogContext.SYMBOL)).isNull();
    }

    @Test
    void nestedScopesRestoreTheOuterValues() {
        try (var outer = LogContext.scope("holders", Symbol.of("AAPL"))) {
            try (var inner = LogContext.scope("quotes", List.of(Symbol.of("MSFT"), Symbol.of("GOOG")))) {
                assertThat(MDC.get(LogContext.OP)).isEqualTo("quotes");
                assertThat(MDC.get(LogContext.SYMBOL)).isEqualTo("MSFT,GOOG");
            }
            assertThat(MDC.get(LogContext.OP)).isEqualTo("holders");
            assertThat(MDC.get(LogContext.SYMBOL)).isEqualTo("AAPL");
        }
        assertThat(MDC.get(LogContext.OP)).isNull();
    }

    @Test
    void scopeWithoutSymbolLeavesSymbolUntouched() {
        MDC.put(LogContext.SYMBOL, "outer");
        try (var ignored = LogContext.scope("search")) {
            assertThat(MDC.get(LogContext.OP)).isEqualTo("search");
            assertThat(MDC.get(LogContext.SYMBOL)).isEqualTo("outer");
        }
        assertThat(MDC.get(LogContext.SYMBOL)).isEqualTo("outer");
    }
}
