package io.ziggy.yfinance.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.ziggy.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceBarTest {

    private static final Instant T = Instant.ofEpochSecond(1_700_000_000L);

    @Test
    void adjustedScalesOhlcByAdjCloseOverClose() {
        // A 4:1 split later on makes adjClose = close / 4; open/high/low must scale the same way.
        var bar = new PriceBar(T, dec("100"), dec("110"), dec("90"), dec("104"), dec("26"), 1_000L);

        var adjusted = bar.adjusted();

        assertThat(adjusted.timestamp()).isEqualTo(T);
        assertThat(adjusted.open()).isEqualByComparingTo("25");
        assertThat(adjusted.high()).isEqualByComparingTo("27.5");
        assertThat(adjusted.low()).isEqualByComparingTo("22.5");
        assertThat(adjusted.close()).isEqualByComparingTo("26");
        assertThat(adjusted.adjClose()).isEqualByComparingTo("26"); // already adjusted: factor is now 1
        assertThat(adjusted.volume()).isEqualTo(1_000L);            // volume is left as reported
        assertThat(adjusted.adjusted()).isEqualTo(adjusted);          // idempotent
    }

    @Test
    void adjustedKeepsNullsAndIsANoOpWithoutAdjClose() {
        var partial = new PriceBar(T, null, dec("110"), null, dec("104"), dec("52"), null);
        var adjusted = partial.adjusted();
        assertThat(adjusted.open()).isNull();
        assertThat(adjusted.high()).isEqualByComparingTo("55");
        assertThat(adjusted.low()).isNull();
        assertThat(adjusted.volume()).isNull();

        var noAdj = new PriceBar(T, dec("100"), dec("110"), dec("90"), dec("104"), null, 1L);
        assertThat(noAdj.adjusted()).isSameAs(noAdj);

        var zeroClose = new PriceBar(T, dec("1"), dec("1"), dec("1"), BigDecimal.ZERO, dec("1"), 1L);
        assertThat(zeroClose.adjusted()).isSameAs(zeroClose);
    }

    @Test
    void priceHistoryAdjustedMapsBarsAndKeepsEverythingElse() {
        var meta = new HistoryMetadata(Symbol.of("AAPL"), null, null, null, null, null, null, null, null, null,
                null, null, List.of(), null, null);
        var history = new PriceHistory(meta,
                List.of(new PriceBar(T, dec("100"), dec("110"), dec("90"), dec("104"), dec("26"), 1L)),
                List.of(new Dividend(T, dec("0.24"))), List.of(), List.of());

        var adjusted = history.adjusted();

        assertThat(adjusted.metadata()).isSameAs(meta);
        assertThat(adjusted.dividends()).isEqualTo(history.dividends());
        assertThat(adjusted.bars()).singleElement()
                .satisfies(b -> assertThat(b.close()).isEqualByComparingTo("26"));
    }

    private static BigDecimal dec(String s) {
        return new BigDecimal(s);
    }
}
