package io.github.dimazigel.yfinance.dto.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Raw deserialization of the {@code /xhr/ncp} news stream response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsResponse(@Nullable Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable TickerStream tickerStream) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TickerStream(@Nullable List<Item> stream) {}

    /** One stream entry; {@code ad} is kept raw because only its presence matters and its shape is not captured. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(@Nullable String id, @Nullable JsonNode ad, @Nullable Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            @Nullable String id,
            @Nullable String contentType,
            @Nullable String title,
            @Nullable String summary,
            @Nullable String pubDate,
            @Nullable Thumbnail thumbnail,
            @Nullable Provider provider,
            @Nullable Link canonicalUrl,
            @Nullable Link clickThroughUrl,
            @Nullable Finance finance) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnail(@Nullable String originalUrl, @Nullable Integer originalWidth, @Nullable Integer originalHeight) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Provider(@Nullable String displayName, @Nullable String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Link(@Nullable String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finance(@Nullable PremiumFinance premiumFinance) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PremiumFinance(@JsonProperty("isPremiumNews") @Nullable Boolean isPremiumNews) {}
}
