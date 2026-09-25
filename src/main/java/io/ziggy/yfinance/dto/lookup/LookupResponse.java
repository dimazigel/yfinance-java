package io.ziggy.yfinance.dto.lookup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.ziggy.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.List;

/** Raw deserialization of the {@code /v1/finance/lookup} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LookupResponse(Finance finance) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finance(List<Result> result, Error error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String code, String description) implements YahooError {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(List<Document> documents) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(String symbol, String shortName, String quoteType, String exchange, BigDecimal regularMarketPrice) {}
}
