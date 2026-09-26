package io.github.dimazigel.yfinance.market;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Optional;

/**
 * A single OHLCV candle.
 *
 * <p>{@code timestamp} and the full OHLC are always present: the mapper drops a bar if any of
 * open/high/low/close is missing (Yahoo pads intraday series with all-null rows for halts and
 * pre-open gaps, and occasionally omits just one of the four).
 *
 * <p>Prices are Yahoo's <em>raw</em> quotes, with the split- and dividend-adjusted close alongside
 * as {@code adjClose}. Python yfinance defaults to adjusted OHLC ({@code auto_adjust=True});
 * {@link #adjusted()} produces the same numbers.
 *
 * @param adjClose split/dividend-adjusted close, absent if not provided by Yahoo
 * @param volume   traded volume, absent when Yahoo reported none — a missing value is deliberately
 *                 not coerced to zero so stored data stays honest
 */
public record PriceBar(
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Optional<BigDecimal> adjClose,
        Optional<Long> volume) {

    private static final MathContext PRECISION = MathContext.DECIMAL64;

    /**
     * This bar with open/high/low/close scaled by {@code adjClose / close}, i.e. Python yfinance's
     * {@code auto_adjust=True} view: prices comparable across splits and dividends. Volume is left
     * as reported (yfinance does the same). Returns this bar unchanged when it carries no
     * {@code adjClose}, its close is zero, or it is already adjusted; idempotent.
     */
    public PriceBar adjusted() {
        if (adjClose.isEmpty() || close.signum() == 0) {
            return this;
        }
        BigDecimal adjustedClose = adjClose.get();
        if (adjustedClose.compareTo(close) == 0) {
            return this;
        }
        BigDecimal factor = adjustedClose.divide(close, PRECISION);
        return new PriceBar(timestamp, open.multiply(factor, PRECISION), high.multiply(factor, PRECISION),
                low.multiply(factor, PRECISION), adjustedClose, adjClose, volume);
    }
}
