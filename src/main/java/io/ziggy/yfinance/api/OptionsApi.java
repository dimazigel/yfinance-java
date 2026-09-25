package io.ziggy.yfinance.api;

import io.ziggy.yfinance.dto.options.OptionChainResponse;
import org.jspecify.annotations.Nullable;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Retrofit binding for Yahoo's options endpoint. */
public interface OptionsApi {

    @GET("v7/finance/options/{symbol}")
    OptionChainResponse options(@Path("symbol") String symbol, @Query("date") @Nullable Long date);
}
