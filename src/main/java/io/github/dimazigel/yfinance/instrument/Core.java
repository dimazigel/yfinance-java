package io.github.dimazigel.yfinance.instrument;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * The universal tier: present for every asset class on both endpoints (Appendix A, "Universal
 * core"). {@code longName} is optional because futures never have one.
 */
public record Core(
        Symbol symbol,
        String shortName,
        Optional<String> longName,
        QuoteCurrency currency,
        String exchange,
        String fullExchangeName,
        ZoneId exchangeTimezone,
        MarketState marketState,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal previousClose,
        Instant priceTime,
        BigDecimal fiftyTwoWeekLow,
        BigDecimal fiftyTwoWeekHigh,
        BigDecimal fiftyDayAverage,
        BigDecimal twoHundredDayAverage,
        long averageVolume10Day,
        long averageVolume3Month,
        Instant firstTradeDate,
        int priceHint,
        boolean hasPrePostMarketData) {}
