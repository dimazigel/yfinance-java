package io.ziggy.yfinance.dto.chart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.ziggy.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Raw deserialization of the {@code /v8/finance/chart} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartResponse(@Nullable Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(@Nullable List<ChartResult> result, @Nullable ChartError error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartError(@Nullable String code, @Nullable String description) implements YahooError {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartResult(@Nullable ChartMeta meta, @Nullable List<Long> timestamp, @Nullable Indicators indicators, @Nullable ChartEvents events) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartMeta(
            @Nullable String currency,
            @Nullable String symbol,
            @Nullable String exchangeName,
            @Nullable String fullExchangeName,
            @Nullable String instrumentType,
            @Nullable Long firstTradeDate,
            @Nullable Long regularMarketTime,
            @Nullable Integer gmtoffset,
            @Nullable String timezone,
            @Nullable String exchangeTimezoneName,
            @Nullable BigDecimal regularMarketPrice,
            @Nullable BigDecimal chartPreviousClose,
            @Nullable Integer priceHint,
            @Nullable String dataGranularity,
            @Nullable List<String> validRanges,
            @Nullable CurrentTradingPeriod currentTradingPeriod,
            @Nullable Boolean hasPrePostMarketData) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentTradingPeriod(
            @Nullable TradingPeriod pre, @Nullable TradingPeriod regular, @Nullable TradingPeriod post) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TradingPeriod(@Nullable String timezone, @Nullable Long start, @Nullable Long end, @Nullable Integer gmtoffset) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(@Nullable List<Quote> quote, @Nullable List<AdjClose> adjclose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(
            @Nullable List<@Nullable BigDecimal> open,
            @Nullable List<@Nullable BigDecimal> high,
            @Nullable List<@Nullable BigDecimal> low,
            @Nullable List<@Nullable BigDecimal> close,
            @Nullable List<@Nullable Long> volume) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdjClose(@Nullable List<@Nullable BigDecimal> adjclose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartEvents(
            @Nullable Map<String, DividendEvent> dividends,
            @Nullable Map<String, SplitEvent> splits,
            @Nullable Map<String, CapitalGainEvent> capitalGains) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DividendEvent(@Nullable BigDecimal amount, @Nullable Long date) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SplitEvent(@Nullable Long date, @Nullable BigDecimal numerator, @Nullable BigDecimal denominator, @Nullable String splitRatio) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CapitalGainEvent(@Nullable BigDecimal amount, @Nullable Long date) {}
}
