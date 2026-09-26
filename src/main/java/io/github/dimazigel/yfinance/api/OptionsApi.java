package io.github.dimazigel.yfinance.api;

import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.dto.options.OptionChainResponse;
import org.jspecify.annotations.Nullable;

/** Feign binding for Yahoo's options endpoint. */
public interface OptionsApi {

    @RequestLine("GET /v7/finance/options/{symbol}?date={date}")
    OptionChainResponse options(@Param("symbol") String symbol, @Param("date") @Nullable Long date);
}
