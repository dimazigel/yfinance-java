package io.ziggy.yfinance.model;

import io.ziggy.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** A single instrument returned by the lookup endpoint. */
public record LookupQuote(
        Symbol symbol,
        @Nullable String shortName,
        @Nullable String quoteType,
        @Nullable String exchange,
        @Nullable BigDecimal regularMarketPrice) {}
