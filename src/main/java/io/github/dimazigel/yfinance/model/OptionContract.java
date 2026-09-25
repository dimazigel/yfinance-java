package io.github.dimazigel.yfinance.model;

import io.github.dimazigel.yfinance.enums.OptionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/** A single option contract (call or put). */
public record OptionContract(
        @Nullable String contractSymbol,
        OptionType type,
        @Nullable BigDecimal strike,
        @Nullable Currency currency,
        @Nullable BigDecimal lastPrice,
        @Nullable BigDecimal bid,
        @Nullable BigDecimal ask,
        @Nullable BigDecimal change,
        @Nullable BigDecimal percentChange,
        @Nullable Long volume,
        @Nullable Long openInterest,
        @Nullable BigDecimal impliedVolatility,
        boolean inTheMoney,
        @Nullable String contractSize,
        @Nullable Instant lastTradeDate,
        @Nullable Instant expiration) {}
