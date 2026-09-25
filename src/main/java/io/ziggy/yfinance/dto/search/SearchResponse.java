package io.ziggy.yfinance.dto.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Raw deserialization of the {@code /v1/finance/search} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResponse(@Nullable List<Quote> quotes, @Nullable List<News> news) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(@Nullable String symbol, @Nullable String shortname, @Nullable String longname, @Nullable String exchange, @Nullable String exchDisp, @Nullable String quoteType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record News(@Nullable String uuid, @Nullable String title, @Nullable String publisher, @Nullable String link, @Nullable Long providerPublishTime, @Nullable String type) {}
}
