package io.ziggy.yfinance.dto.chart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.ziggy.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Raw deserialization of the {@code /v8/finance/chart} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartResponse(Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<ChartResult> result, ChartError error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartError(String code, String description) implements YahooError {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartResult(ChartMeta meta, List<Long> timestamp, Indicators indicators, ChartEvents events) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartMeta(
            String currency,
            String symbol,
            String exchangeName,
            String fullExchangeName,
            String instrumentType,
            Long firstTradeDate,
            Long regularMarketTime,
            Integer gmtoffset,
            String timezone,
            String exchangeTimezoneName,
            BigDecimal regularMarketPrice,
            BigDecimal chartPreviousClose,
            Integer priceHint) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(List<Quote> quote, List<AdjClose> adjclose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(
            List<BigDecimal> open,
            List<BigDecimal> high,
            List<BigDecimal> low,
            List<BigDecimal> close,
            List<Long> volume) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdjClose(List<BigDecimal> adjclose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartEvents(
            Map<String, DividendEvent> dividends,
            Map<String, SplitEvent> splits,
            Map<String, CapitalGainEvent> capitalGains) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DividendEvent(BigDecimal amount, Long date) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SplitEvent(Long date, BigDecimal numerator, BigDecimal denominator, String splitRatio) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CapitalGainEvent(BigDecimal amount, Long date) {}
}
