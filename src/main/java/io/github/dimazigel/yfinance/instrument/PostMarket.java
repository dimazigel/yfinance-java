package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;
import java.time.Instant;

/** After-hours print; present only when there was one (time-of-day, not asset class). */
public record PostMarket(BigDecimal price, BigDecimal change, BigDecimal changePercent, Instant time) {}
