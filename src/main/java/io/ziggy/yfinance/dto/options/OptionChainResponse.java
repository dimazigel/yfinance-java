package io.ziggy.yfinance.dto.options;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.ziggy.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.List;

/** Raw deserialization of the {@code /v7/finance/options} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OptionChainResponse(OptionChain optionChain) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionChain(List<Result> result, Error error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String code, String description) implements YahooError {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            String underlyingSymbol,
            List<Long> expirationDates,
            List<BigDecimal> strikes,
            List<OptionsByExpiration> options) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionsByExpiration(Long expirationDate, List<Contract> calls, List<Contract> puts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contract(
            String contractSymbol,
            BigDecimal strike,
            String currency,
            BigDecimal lastPrice,
            BigDecimal change,
            BigDecimal percentChange,
            Long volume,
            Long openInterest,
            BigDecimal bid,
            BigDecimal ask,
            String contractSize,
            Long expiration,
            Long lastTradeDate,
            BigDecimal impliedVolatility,
            Boolean inTheMoney) {}
}
