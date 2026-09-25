package io.github.dimazigel.yfinance.dto.timeseries;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.dimazigel.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Raw deserialization of the fundamentals timeseries response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TimeseriesResponse(@Nullable Timeseries timeseries) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Timeseries(@Nullable List<Result> result, @Nullable ErrorBody error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(@Nullable String code, @Nullable String description) implements YahooError {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(@Nullable List<String> symbol, @Nullable List<String> type) {}

    /**
     * A single timeseries result. {@code meta} and {@code timestamp} are explicit; every other
     * property is a dynamically-named metric array (e.g. {@code annualTotalRevenue}) captured via
     * {@link JsonAnySetter} using its declared {@code List<DataPoint>} type.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Result {
        public @Nullable Meta meta;
        public @Nullable List<Long> timestamp;
        private final Map<String, List<@Nullable DataPoint>> series = new LinkedHashMap<>();

        @JsonAnySetter
        void put(String key, List<@Nullable DataPoint> value) {
            series.put(key, value);
        }

        public Map<String, List<@Nullable DataPoint>> series() {
            return series;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataPoint(
            @Nullable Integer dataId, @Nullable String asOfDate, @Nullable String periodType, @Nullable String currencyCode, @Nullable ReportedValue reportedValue) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReportedValue(@Nullable BigDecimal raw, @Nullable String fmt) {}
}
