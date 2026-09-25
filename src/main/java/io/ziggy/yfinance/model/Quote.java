package io.ziggy.yfinance.model;

import io.ziggy.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/**
 * A consolidated market quote drawn from the {@code price}, {@code summaryDetail},
 * {@code financialData}, {@code defaultKeyStatistics} and {@code quoteType} modules,
 * grouped into identity, {@link PriceSnapshot}, {@link KeyStats} and {@link AnalystSummary}.
 */
public record Quote(
        Symbol symbol,
        @Nullable String longName,
        @Nullable String shortName,
        @Nullable String quoteType,
        @Nullable String exchange,
        @Nullable Currency currency,
        @Nullable String marketState,
        PriceSnapshot price,
        KeyStats keyStats,
        AnalystSummary analyst) {

    /** Current market prices and trading ranges. */
    public record PriceSnapshot(
            @Nullable BigDecimal regularMarketPrice,
            @Nullable BigDecimal regularMarketChange,
            @Nullable BigDecimal regularMarketChangePercent,
            @Nullable BigDecimal previousClose,
            @Nullable BigDecimal open,
            @Nullable BigDecimal dayLow,
            @Nullable BigDecimal dayHigh,
            @Nullable Long volume,
            @Nullable BigDecimal fiftyTwoWeekLow,
            @Nullable BigDecimal fiftyTwoWeekHigh,
            @Nullable BigDecimal marketCap) {}

    /** Valuation and share statistics. */
    public record KeyStats(
            @Nullable BigDecimal trailingPe,
            @Nullable BigDecimal trailingEps,
            @Nullable BigDecimal forwardEps,
            @Nullable BigDecimal bookValue,
            @Nullable BigDecimal priceToBook,
            @Nullable BigDecimal beta,
            @Nullable Long sharesOutstanding,
            @Nullable BigDecimal dividendYield) {}

    /** Analyst consensus figures. */
    public record AnalystSummary(
            @Nullable BigDecimal targetMeanPrice,
            @Nullable BigDecimal recommendationMean,
            @Nullable String recommendationKey,
            @Nullable Integer numberOfAnalystOpinions,
            @Nullable BigDecimal totalRevenue,
            @Nullable BigDecimal profitMargins) {}
}
