package io.github.dimazigel.yfinance.market;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Aggregates finer candles into coarser, epoch-aligned buckets (like pandas {@code resample}):
 * open = first, high = max, low = min, close/adjClose = last, volume = sum over present values
 * (absent when none of the source bars reported one). Buckets without any source bar are not
 * emitted.
 */
public final class PriceBarResampler {

    private PriceBarResampler() {}

    /** Resamples time-ordered {@code bars} into buckets of {@code bucket} length. */
    public static List<PriceBar> resample(List<PriceBar> bars, Duration bucket) {
        long size = bucket.toSeconds();
        var out = new ArrayList<PriceBar>();
        Bucket current = null;
        for (PriceBar bar : bars) {
            long start = Math.floorDiv(bar.timestamp().getEpochSecond(), size) * size;
            if (current == null || current.start != start) {
                if (current != null) {
                    out.add(current.toBar());
                }
                current = new Bucket(start, bar);
            } else {
                current.add(bar);
            }
        }
        if (current != null) {
            out.add(current.toBar());
        }
        return out;
    }

    private static final class Bucket {
        private final long start;
        private final BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close; // every bucket holds at least one bar, and bars always have a close
        private Optional<BigDecimal> adjClose;
        private Optional<Long> volume;

        Bucket(long start, PriceBar first) {
            this.start = start;
            this.open = first.open();
            this.high = first.high();
            this.low = first.low();
            this.close = first.close();
            this.adjClose = first.adjClose();
            this.volume = first.volume();
        }

        void add(PriceBar bar) {
            if (bar.high().compareTo(high) > 0) {
                high = bar.high();
            }
            if (bar.low().compareTo(low) < 0) {
                low = bar.low();
            }
            close = bar.close();
            if (bar.adjClose().isPresent()) {
                adjClose = bar.adjClose();
            }
            bar.volume().ifPresent(v -> volume = Optional.of(volume.map(sum -> sum + v).orElse(v)));
        }

        PriceBar toBar() {
            return new PriceBar(Instant.ofEpochSecond(start), open, high, low, close, adjClose, volume);
        }
    }
}
