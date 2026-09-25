package io.github.dimazigel.yfinance.dto.quote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.dimazigel.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Raw deserialization of the {@code /v7/finance/quote} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteResponse(@Nullable Envelope quoteResponse) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(@Nullable List<Result> result, @Nullable Error error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(@Nullable String code, @Nullable String description) implements YahooError {}

    /**
     * One quoted instrument. Note {@code trailingAnnualDividendYield} is a fraction (0.0031) whereas
     * {@code dividendYield} on this endpoint is a percentage (0.31); the fraction is the one that
     * matches quoteSummary's {@code summaryDetail.dividendYield}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @Nullable String symbol,
            @Nullable String shortName,
            @Nullable String longName,
            @Nullable String quoteType,
            @Nullable String currency,
            @Nullable String exchange,
            @Nullable String fullExchangeName,
            @Nullable String marketState,
            @Nullable String exchangeTimezoneName,
            @Nullable BigDecimal regularMarketPrice,
            @Nullable BigDecimal regularMarketChange,
            @Nullable BigDecimal regularMarketChangePercent,
            @Nullable BigDecimal regularMarketPreviousClose,
            @Nullable BigDecimal regularMarketOpen,
            @Nullable BigDecimal regularMarketDayLow,
            @Nullable BigDecimal regularMarketDayHigh,
            @Nullable Long regularMarketVolume,
            @Nullable BigDecimal fiftyTwoWeekLow,
            @Nullable BigDecimal fiftyTwoWeekHigh,
            @Nullable BigDecimal marketCap,
            @Nullable BigDecimal trailingPE,
            @Nullable BigDecimal epsTrailingTwelveMonths,
            @Nullable BigDecimal epsForward,
            @Nullable BigDecimal bookValue,
            @Nullable BigDecimal priceToBook,
            @Nullable Long sharesOutstanding,
            @Nullable BigDecimal trailingAnnualDividendYield,
            @Nullable String averageAnalystRating,
            @Nullable Long regularMarketTime) {}
}
