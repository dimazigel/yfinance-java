package io.github.dimazigel.yfinance.api;

import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.dto.chart.ChartResponse;
import org.jspecify.annotations.Nullable;

/** Feign binding for Yahoo's price-history (chart) endpoint. */
public interface ChartApi {

    @RequestLine("GET /v8/finance/chart/{symbol}?interval={interval}&range={range}&period1={period1}"
            + "&period2={period2}&includePrePost={includePrePost}&events={events}")
    ChartResponse chart(
            @Param("symbol") String symbol,
            @Param("interval") String interval,
            @Param("range") @Nullable String range,
            @Param("period1") @Nullable Long period1,
            @Param("period2") @Nullable Long period2,
            @Param("includePrePost") boolean includePrePost,
            @Param("events") @Nullable String events);
}
