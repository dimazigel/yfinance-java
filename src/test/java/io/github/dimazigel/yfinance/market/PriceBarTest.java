package io.github.dimazigel.yfinance.market;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PriceBarTest {

    private static final Instant T = Instant.ofEpochSecond(1_700_000_000L);

    @Test
    void adjustedScalesOhlcByAdjCloseOverClose() {
        // A 4:1 split later on makes adjClose = close / 4; open/high/low must scale the same way.
        var bar = new PriceBar(T, dec("100"), dec("110"), dec("90"), dec("104"), Optional.of(dec("26")), Optional.of(1_000L));

        var adjusted = bar.adjusted();

        assertThat(adjusted.timestamp()).isEqualTo(T);
        assertThat(adjusted.open()).isEqualByComparingTo("25");
        assertThat(adjusted.high()).isEqualByComparingTo("27.5");
        assertThat(adjusted.low()).isEqualByComparingTo("22.5");
        assertThat(adjusted.close()).isEqualByComparingTo("26");
        assertThat(adjusted.adjClose().orElseThrow()).isEqualByComparingTo("26"); // already adjusted: factor is now 1
        assertThat(adjusted.volume()).contains(1_000L);                          // volume is left as reported
        assertThat(adjusted.adjusted()).isEqualTo(adjusted);                     // idempotent
    }

    @Test
    void adjustedIsANoOpWithoutAdjCloseOrWhenAlreadyAdjusted() {
        var noAdjClose = new PriceBar(T, dec("100"), dec("110"), dec("90"), dec("104"), Optional.empty(), Optional.of(1L));
        assertThat(noAdjClose.adjusted()).isSameAs(noAdjClose);

        var zeroClose = new PriceBar(T, dec("1"), dec("1"), dec("1"), BigDecimal.ZERO, Optional.of(dec("1")), Optional.of(1L));
        assertThat(zeroClose.adjusted()).isSameAs(zeroClose);

        var alreadyAdjusted = new PriceBar(T, dec("100"), dec("110"), dec("90"), dec("104"), Optional.of(dec("104")), Optional.empty());
        assertThat(alreadyAdjusted.adjusted()).isSameAs(alreadyAdjusted);
        assertThat(alreadyAdjusted.volume()).isEmpty(); // missing volume is not coerced, on the adjusted view either
    }

    @Test
    void priceHistoryAdjustedMapsBarsAndKeepsEverythingElse() {
        var meta = fullMetadata();
        var history = new PriceHistory(meta,
                List.of(new PriceBar(T, dec("100"), dec("110"), dec("90"), dec("104"), Optional.of(dec("26")), Optional.of(1L))),
                List.of(new Dividend(T, dec("0.24"))), List.of(), List.of());

        var adjusted = history.adjusted();

        assertThat(adjusted.metadata()).isSameAs(meta);
        assertThat(adjusted.dividends()).isEqualTo(history.dividends());
        assertThat(adjusted.bars()).singleElement()
                .satisfies(b -> assertThat(b.close()).isEqualByComparingTo("26"));
    }

    private static HistoryMetadata fullMetadata() {
        var session = new HistoryMetadata.TradingPeriod(T, T.plusSeconds(3600));
        return new HistoryMetadata(Symbol.of("AAPL"), QuoteCurrency.of("USD"), "NMS", "NasdaqGS", "EQUITY",
                ZoneOffset.UTC, T, dec("100"), dec("99"), T, 2, Optional.of(Interval.ONE_DAY),
                List.of(Range.ONE_DAY), new HistoryMetadata.TradingPeriods(session, session, session), true);
    }

    private static BigDecimal dec(String s) {
        return new BigDecimal(s);
    }
}
