package io.github.dimazigel.yfinance.internal.mapper;

import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.internal.dto.news.NewsResponse;
import io.github.dimazigel.yfinance.news.NewsItem;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps the news gateway's stream to {@link NewsItem}s, keeping Yahoo's order.
 *
 * <p>An entry whose asset is a {@code Story} or {@code Video} is mapped; one missing a required
 * field ({@code id}, {@code title}, {@code pubDate}, {@code canonicalUrl},
 * {@code provider.displayName}) is dropped with one DEBUG line per stream. An {@code AssetError}
 * entry — what Yahoo returns past the entries it can hydrate, roughly the first fifty — carries
 * nothing and is dropped the same way, counted separately. A missing stream is an empty list, unless
 * the gateway reported GraphQL {@code errors}, which is a {@link YFDataException}.
 */
public final class NewsMapper {

    private static final Logger LOG = LoggerFactory.getLogger(NewsMapper.class);

    private NewsMapper() {}

    public static List<NewsItem> toItems(NewsResponse response) {
        var items = new ArrayList<NewsItem>();
        int total = 0;
        int unhydrated = 0;
        for (NewsResponse.Edge edge : edges(response)) {
            var node = edge.node();
            var asset = node == null ? null : node.asset();
            if (asset == null || !isContent(asset.typename())) {
                unhydrated++;
                continue;
            }
            total++;
            NewsItem item = toItem(asset);
            if (item != null) {
                items.add(item);
            }
        }
        int dropped = total - items.size();
        if (dropped > 0) {
            LOG.atDebug().addKeyValue("dropped", dropped).addKeyValue("total", total)
                    .log("Dropped {} of {} news items without a complete required field", dropped, total);
        }
        if (unhydrated > 0) {
            LOG.atDebug().addKeyValue("unhydrated", unhydrated).addKeyValue("total", total + unhydrated)
                    .log("Dropped {} of {} news entries Yahoo could not hydrate", unhydrated, total + unhydrated);
        }
        return List.copyOf(items);
    }

    private static List<NewsResponse.Edge> edges(NewsResponse response) {
        var data = response.data();
        var list = data == null ? null : data.lightyearList();
        var main = list == null ? null : list.main();
        var edges = main == null ? null : main.edges();
        if (edges == null) {
            var errors = response.errors();
            if (errors != null && !errors.isEmpty()) {
                throw new YFDataException("Yahoo's news gateway answered with errors instead of a stream: "
                        + errors.stream().map(NewsResponse.GraphqlError::message).filter(Objects::nonNull).toList());
            }
            return List.of();
        }
        return edges;
    }

    private static boolean isContent(@Nullable String typename) {
        return "Story".equals(typename) || "Video".equals(typename);
    }

    private static @Nullable NewsItem toItem(NewsResponse.Asset asset) {
        var content = asset.contentAttributes();
        String id = text(asset.id());
        String title = text(asset.title());
        Instant published = content == null ? null : MapperSupport.instant(content.pubDate());
        URI url = content == null ? null : MapperSupport.uri(content.canonicalUrl());
        NewsItem.Provider provider = content == null ? null : provider(content.provider());
        if (id == null || title == null || published == null || url == null || provider == null || content == null) {
            return null;
        }
        return new NewsItem(
                id,
                title,
                published,
                url,
                provider,
                Optional.ofNullable(asset.typename()).map(t -> t.toUpperCase(Locale.ROOT)),
                Optional.ofNullable(text(content.summary())),
                Optional.ofNullable(MapperSupport.uri(content.clickthroughUrl())),
                Optional.ofNullable(thumbnail(content.thumbnail())),
                Optional.ofNullable(asset.isPremiumNews()));
    }

    private static NewsItem.@Nullable Provider provider(NewsResponse.@Nullable Provider provider) {
        String name = provider == null ? null : text(provider.displayName());
        if (provider == null || name == null) {
            return null;
        }
        return new NewsItem.Provider(name, Optional.ofNullable(MapperSupport.uri(provider.url())));
    }

    private static NewsItem.@Nullable Thumbnail thumbnail(NewsResponse.@Nullable Thumbnail thumbnail) {
        if (thumbnail == null) {
            return null;
        }
        URI url = MapperSupport.uri(thumbnail.originalUrl());
        Integer width = thumbnail.originalWidth();
        Integer height = thumbnail.originalHeight();
        if (url == null || width == null || height == null) {
            return null;
        }
        return new NewsItem.Thumbnail(url, width, height);
    }

    private static @Nullable String text(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
