package io.github.dimazigel.yfinance.dto.lookup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.dimazigel.yfinance.dto.YahooError;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Raw deserialization of the {@code /v1/finance/lookup} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LookupResponse(@Nullable Finance finance) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finance(@Nullable List<Result> result, @Nullable ErrorBody error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(@Nullable String code, @Nullable String description) implements YahooError {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(@Nullable List<Document> documents) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(@Nullable String symbol, @Nullable String shortName, @Nullable String quoteType, @Nullable String exchange, @Nullable BigDecimal regularMarketPrice) {}
}
