package io.ziggy.yfinance.model;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A single OHLCV candle.
 *
 * <p>{@code timestamp} and {@code close} are always present (bars without a close are dropped at
 * mapping time); every other field is {@code null} when Yahoo did not report it.
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
        @Nullable Long volume) {}
