package io.ziggy.yfinance.model;

import io.ziggy.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/** Instrument metadata accompanying a price-history response. */
public record HistoryMetadata(
        Symbol symbol,
        @Nullable Currency currency,
        @Nullable String exchangeName,
        @Nullable String fullExchangeName,
        @Nullable String instrumentType,
        @Nullable ZoneId timezone,
        @Nullable Instant firstTradeDate,
        @Nullable BigDecimal regularMarketPrice,
        @Nullable BigDecimal previousClose) {}
