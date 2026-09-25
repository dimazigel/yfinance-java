package io.ziggy.yfinance.dto.timeseries;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.ziggy.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Raw deserialization of the fundamentals timeseries response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TimeseriesResponse(Timeseries timeseries) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Timeseries(List<Result> result, Error error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String code, String description) implements YahooError {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(List<String> symbol, List<String> type) {}

    /**
     * A single timeseries result. {@code meta} and {@code timestamp} are explicit; every other
     * property is a dynamically-named metric array (e.g. {@code annualTotalRevenue}) captured via
     * {@link JsonAnySetter} using its declared {@code List<DataPoint>} type.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Result {
        public Meta meta;
        public List<Long> timestamp;
        private final Map<String, List<DataPoint>> series = new LinkedHashMap<>();

        @JsonAnySetter
        void put(String key, List<DataPoint> value) {
            series.put(key, value);
        }

        public Map<String, List<DataPoint>> series() {
            return series;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataPoint(
            Integer dataId, String asOfDate, String periodType, String currencyCode, ReportedValue reportedValue) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReportedValue(BigDecimal raw, String fmt) {}
}
