package io.ziggy.yfinance.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ziggy.yfinance.valueobject.Symbol;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HistoryRequestTest {

    private static final Symbol AAPL = Symbol.of("AAPL");

    @Test
    void rejectsEndNotAfterStart() {
        var t = Instant.ofEpochSecond(2000);
        assertThatThrownBy(() -> HistoryRequest.builder(AAPL).period(t, Instant.ofEpochSecond(1000)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end");
        assertThatThrownBy(() -> HistoryRequest.builder(AAPL).period(t, t).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsOpenEndedAndOrderedWindows() {
        assertThatCode(() -> HistoryRequest.builder(AAPL).period(Instant.ofEpochSecond(1000), null).build())
                .doesNotThrowAnyException();
        assertThatCode(() -> HistoryRequest.builder(AAPL)
                        .period(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1001)).build())
                .doesNotThrowAnyException();
    }

    @Test
    void requiresRangeOrPeriod() {
        assertThatThrownBy(() -> new HistoryRequest(AAPL, io.ziggy.yfinance.enums.Interval.ONE_DAY, null, null, null, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
