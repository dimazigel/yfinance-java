package io.github.dimazigel.yfinance.internal.mapper;

import io.github.dimazigel.yfinance.internal.dto.news.NewsResponse;
import io.github.dimazigel.yfinance.news.NewsItem;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/** Maps the news stream response to {@link NewsItem}s. */
public final class NewsMapper {

    private static final Logger LOG = LoggerFactory.getLogger(NewsMapper.class);

    private NewsMapper() {}

    /**
     * The stream's items in Yahoo's order, advertisements filtered out. An item missing a required
     * field is dropped; a response without a stream (an unknown symbol, a tab with nothing in it) is
     * an empty list.
     */
    public static List<NewsItem> toItems(NewsResponse response) {
        var items = new ArrayList<NewsItem>();
        int total = 0;
        for (NewsResponse.Item entry : stream(response)) {
            if (isAd(entry.ad())) {
                continue;
            }
            total++;
            NewsItem item = toItem(entry.content());
            if (item != null) {
                items.add(item);
            }
        }
        int dropped = total - items.size();
        if (dropped > 0) {
            LOG.atDebug().addKeyValue("dropped", dropped).addKeyValue("total", total)
                    .log("Dropped {} of {} news items without a complete required field", dropped, total);
        }
        return List.copyOf(items);
    }

    private static List<NewsResponse.Item> stream(NewsResponse response) {
        var data = response.data();
        var tickerStream = data == null ? null : data.tickerStream();
        var stream = tickerStream == null ? null : tickerStream.stream();
        return stream == null ? List.of() : stream;
    }

    /**
     * A sponsored stream entry carries a non-empty {@code ad} member — according to Python yfinance,
     * which filters on it. This endpoint has never been seen to serve one: 3,469 entries probed
     * live on 2026-10-01 (five symbols, all three tabs, up to 200 items each) all had exactly
     * {@code id} and {@code content}. The filter is kept as upstream's precaution; the shape it
     * tests for is upstream's, not an observed one.
     */
    private static boolean isAd(@Nullable JsonNode ad) {
        if (ad == null) {
            return false;
        }
        return (ad.isArray() || ad.isObject()) ? !ad.isEmpty() : ad.asBoolean(false);
    }

    private static @Nullable NewsItem toItem(NewsResponse.@Nullable Content content) {
        if (content == null) {
            return null;
        }
        String id = text(content.id());
        String title = text(content.title());
        Instant published = MapperSupport.instant(content.pubDate());
        URI url = link(content.canonicalUrl());
        NewsItem.Provider provider = provider(content.provider());
        if (id == null || title == null || published == null || url == null || provider == null) {
            return null;
        }
        return new NewsItem(
                id,
                title,
                published,
                url,
                provider,
                Optional.ofNullable(text(content.contentType())),
                Optional.ofNullable(text(content.summary())),
                Optional.ofNullable(link(content.clickThroughUrl())),
                Optional.ofNullable(thumbnail(content.thumbnail())),
                Optional.ofNullable(premium(content.finance())));
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

    private static @Nullable Boolean premium(NewsResponse.@Nullable Finance finance) {
        var premiumFinance = finance == null ? null : finance.premiumFinance();
        return premiumFinance == null ? null : premiumFinance.isPremiumNews();
    }

    private static @Nullable URI link(NewsResponse.@Nullable Link link) {
        return link == null ? null : MapperSupport.uri(link.url());
    }

    /** Yahoo sends {@code ""} as well as {@code null} for a string it has no value for. */
    private static @Nullable String text(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
