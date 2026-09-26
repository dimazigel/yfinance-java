package io.github.dimazigel.yfinance.api;

import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.dto.timeseries.TimeseriesResponse;

/** Feign binding for Yahoo's fundamentals timeseries endpoint. */
public interface FundamentalsApi {

    @RequestLine("GET /ws/fundamentals-timeseries/v1/finance/timeseries/{symbol}?type={type}&period1={period1}&period2={period2}")
    TimeseriesResponse timeseries(
            @Param("symbol") String symbol,
            @Param("type") String type,
            @Param("period1") long period1,
            @Param("period2") long period2);
}
