package io.ziggy.yfinance.mapper;

import io.ziggy.yfinance.model.PriceBar;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates finer candles into coarser, epoch-aligned buckets (like pandas {@code resample}):
 * open = first, high = max, low = min, close/adjClose = last, volume = sum. Missing values are
 * skipped rather than propagated, and a bucket whose volumes are all missing keeps a {@code null}
 * volume. Buckets without any source bar are not emitted.
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
                current = new Bucket(start);
            }
            current.add(bar);
        }
        if (current != null) {
            out.add(current.toBar());
        }
        return out;
    }

    private static final class Bucket {
        private final long start;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal adjClose;
        private Long volume;

        Bucket(long start) {
            this.start = start;
        }

        void add(PriceBar bar) {
            if (open == null) {
                open = bar.open();
            }
            if (bar.high() != null && (high == null || bar.high().compareTo(high) > 0)) {
                high = bar.high();
            }
            if (bar.low() != null && (low == null || bar.low().compareTo(low) < 0)) {
                low = bar.low();
            }
            if (bar.close() != null) {
                close = bar.close();
            }
            if (bar.adjClose() != null) {
                adjClose = bar.adjClose();
            }
            if (bar.volume() != null) {
                volume = volume == null ? bar.volume() : volume + bar.volume();
            }
        }

        PriceBar toBar() {
            return new PriceBar(Instant.ofEpochSecond(start), open, high, low, close, adjClose, volume);
        }
    }
}
