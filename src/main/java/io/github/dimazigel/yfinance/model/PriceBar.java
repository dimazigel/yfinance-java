package io.github.dimazigel.yfinance.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A single OHLCV candle.
 *
 * <p>{@code timestamp} and {@code close} are always present (bars without a close are dropped at
 * mapping time); every other field is {@code null} when Yahoo did not report it.
 *
 * <p>Prices are Yahoo's <em>raw</em> quotes, with the split- and dividend-adjusted close alongside
 * as {@code adjClose}. Python yfinance defaults to adjusted OHLC ({@code auto_adjust=True});
 * {@link #adjusted()} produces the same numbers.
 *
 * @param adjClose split/dividend-adjusted close, or {@code null} if not provided by Yahoo
 * @param volume   traded volume, or {@code null} when Yahoo reported none — a missing value is
 *                 deliberately not coerced to zero so stored data stays honest
 */
public record PriceBar(
        Instant timestamp,
        @Nullable BigDecimal open,
        @Nullable BigDecimal high,
        @Nullable BigDecimal low,
        BigDecimal close,
        @Nullable BigDecimal adjClose,
        @Nullable Long volume) {

    private static final MathContext PRECISION = MathContext.DECIMAL64;

    /**
     * This bar with open/high/low/close scaled by {@code adjClose / close}, i.e. Python yfinance's
     * {@code auto_adjust=True} view: prices comparable across splits and dividends. Volume is left
     * as reported (yfinance does the same). Returns this bar unchanged when it carries no
     * {@code adjClose} or its close is zero; idempotent.
     */
    public PriceBar adjusted() {
        if (adjClose == null || close.signum() == 0) {
            return this;
        }
        if (adjClose.compareTo(close) == 0) {
            return this;
        }
        BigDecimal factor = adjClose.divide(close, PRECISION);
        return new PriceBar(timestamp, scale(open, factor), scale(high, factor), scale(low, factor),
                adjClose, adjClose, volume);
    }

    private static @Nullable BigDecimal scale(@Nullable BigDecimal value, BigDecimal factor) {
        return value == null ? null : value.multiply(factor, PRECISION);
    }
}
