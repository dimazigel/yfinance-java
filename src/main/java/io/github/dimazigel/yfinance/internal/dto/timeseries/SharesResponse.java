package io.github.dimazigel.yfinance.internal.dto.timeseries;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Raw deserialization of the shares-outstanding timeseries response ({@code type=shares_out}).
 * Unlike the statement timeseries, Yahoo reports {@code shares_out} as plain longs rather than
 * {@code {reportedValue}} objects, so it needs its own shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SharesResponse(@Nullable Timeseries timeseries) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Timeseries(@Nullable List<Result> result, TimeseriesResponse.@Nullable ErrorBody error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            TimeseriesResponse.@Nullable Meta meta,
            @Nullable List<@Nullable Long> timestamp,
            @JsonProperty("shares_out") @Nullable List<@Nullable Long> sharesOut) {}
}
