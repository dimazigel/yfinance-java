package io.github.dimazigel.yfinance.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PriceBarResamplerTest {

    private static final long T0 = 1_699_999_200L; // divisible by 1800: a 30m bucket boundary

    @Test
    void aggregatesOhlcvIntoEpochAlignedBuckets() {
        var bars = List.of(
                bar(T0, "10", "12", "9", "11", "10.9", 100L),
                bar(T0 + 900, "11", "13", "10", "12", "11.9", 50L),
                bar(T0 + 1800, "12", "12.5", "11.5", "12.2", "12.1", 70L));

        var resampled = PriceBarResampler.resample(bars, Duration.ofMinutes(30));

        assertThat(resampled).hasSize(2);
        var first = resampled.getFirst();
        assertThat(first.timestamp()).isEqualTo(Instant.ofEpochSecond(T0));
        assertThat(first.open()).isEqualByComparingTo("10");
        assertThat(first.high()).isEqualByComparingTo("13");
        assertThat(first.low()).isEqualByComparingTo("9");
        assertThat(first.close()).isEqualByComparingTo("12");
        assertThat(first.adjClose().orElseThrow()).isEqualByComparingTo("11.9");
        assertThat(first.volume()).contains(150L);
        assertThat(resampled.getLast().timestamp()).isEqualTo(Instant.ofEpochSecond(T0 + 1800));
        assertThat(resampled.getLast().volume()).contains(70L);
    }

    @Test
    void bucketStartsAtBoundaryEvenWhenFirstBarIsMidBucket() {
        var resampled = PriceBarResampler.resample(
                List.of(bar(T0 + 900, "1", "1", "1", "1", null, 5L)), Duration.ofMinutes(30));

        assertThat(resampled).singleElement()
                .satisfies(b -> assertThat(b.timestamp()).isEqualTo(Instant.ofEpochSecond(T0)));
    }

    @Test
    void adjCloseKeepsLastPresentValueAndIsEmptyWhenNeverReported() {
        var bars = List.of(
                bar(T0, "10", "12", "9", "11", null, null),
                bar(T0 + 900, "11", "13", "10", "12", "11.9", null));

        var b = PriceBarResampler.resample(bars, Duration.ofMinutes(30)).getFirst();
        assertThat(b.adjClose()).contains(new BigDecimal("11.9"));

        var withoutAdjClose = PriceBarResampler.resample(
                List.of(bar(T0, "1", "1", "1", "1", null, null)), Duration.ofMinutes(30)).getFirst();
        assertThat(withoutAdjClose.adjClose()).isEmpty();
    }

    @Test
    void volumeSumsPresentValuesAndIsEmptyWhenNonePresent() {
        var bars = List.of(
                bar(T0, "1", "1", "1", "1", null, null),
                bar(T0 + 900, "1", "1", "1", "1", null, 5L));

        var b = PriceBarResampler.resample(bars, Duration.ofMinutes(30)).getFirst();
        assertThat(b.volume()).contains(5L); // missing values are skipped, not coerced to 0

        var withoutVolume = PriceBarResampler.resample(
                List.of(bar(T0, "1", "1", "1", "1", null, null)), Duration.ofMinutes(30)).getFirst();
        assertThat(withoutVolume.volume()).isEmpty();
    }

    @Test
    void emptyGapsProduceNoBars() {
        var bars = List.of(
                bar(T0, "1", "1", "1", "1", null, 1L),
                bar(T0 + 7200, "2", "2", "2", "2", null, 2L)); // 2h later

        assertThat(PriceBarResampler.resample(bars, Duration.ofMinutes(30)))
                .extracting(PriceBar::timestamp)
                .containsExactly(Instant.ofEpochSecond(T0), Instant.ofEpochSecond(T0 + 7200));
    }

    private static PriceBar bar(long epoch, String o, String h, String l, String c, String adj, Long v) {
        return new PriceBar(Instant.ofEpochSecond(epoch), dec(o), dec(h), dec(l), dec(c),
                Optional.ofNullable(dec(adj)), Optional.ofNullable(v));
    }

    private static BigDecimal dec(String s) {
        return s == null ? null : new BigDecimal(s);
    }
}
