package io.github.dimazigel.yfinance.dto.options;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.dimazigel.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Raw deserialization of the {@code /v7/finance/options} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OptionChainResponse(@Nullable OptionChain optionChain) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionChain(@Nullable List<Result> result, @Nullable ErrorBody error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(@Nullable String code, @Nullable String description) implements YahooError {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @Nullable String underlyingSymbol,
            @Nullable List<Long> expirationDates,
            @Nullable List<@Nullable BigDecimal> strikes,
            @Nullable List<OptionsByExpiration> options) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionsByExpiration(@Nullable Long expirationDate, @Nullable List<Contract> calls, @Nullable List<Contract> puts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contract(
            @Nullable String contractSymbol,
            @Nullable BigDecimal strike,
            @Nullable String currency,
            @Nullable BigDecimal lastPrice,
            @Nullable BigDecimal change,
            @Nullable BigDecimal percentChange,
            @Nullable Long volume,
            @Nullable Long openInterest,
            @Nullable BigDecimal bid,
            @Nullable BigDecimal ask,
            @Nullable String contractSize,
            @Nullable Long expiration,
            @Nullable Long lastTradeDate,
            @Nullable BigDecimal impliedVolatility,
            @Nullable Boolean inTheMoney) {}
}
