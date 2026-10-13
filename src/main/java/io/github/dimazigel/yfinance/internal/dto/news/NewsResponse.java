package io.github.dimazigel.yfinance.internal.dto.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Raw deserialization of the news gateway's answer to {@link NewsRequest#QUERY}: the stream is
 * {@code data.lightyearList.main.edges[].node.asset}, and a GraphQL failure comes as
 * {@code errors} with the stream null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsResponse(@Nullable Data data, @Nullable List<GraphqlError> errors) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@Nullable LightyearList lightyearList) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LightyearList(@Nullable Main main) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(@Nullable List<Edge> edges, @Nullable Integer totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Edge(@Nullable Node node) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Node(@Nullable Asset asset) {}

    /**
     * One stream entry: a {@code Story} or {@code Video} carries the item, an {@code AssetError} is
     * an entry Yahoo could not hydrate and has nothing else.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Asset(
            @JsonProperty("__typename") @Nullable String typename,
            @Nullable String id,
            @Nullable String title,
            @JsonProperty("isPremiumNews") @Nullable Boolean isPremiumNews,
            @Nullable ContentAttributes contentAttributes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentAttributes(
            @Nullable String summary,
            @Nullable String pubDate,
            @Nullable String canonicalUrl,
            @Nullable String clickthroughUrl,
            @Nullable Provider provider,
            @Nullable Thumbnail thumbnail) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Provider(@Nullable String displayName, @Nullable String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnail(@Nullable String originalUrl, @Nullable Integer originalWidth, @Nullable Integer originalHeight) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphqlError(@Nullable String message) {}
}
