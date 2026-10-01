package io.github.dimazigel.yfinance.news;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/**
 * One entry of a symbol's news stream: an article, a video or a press release.
 *
 * <p>The non-{@code Optional} components were present on every item surveyed (the 60 captured as
 * fixtures, and 2,763 fetched live over five symbols and all three tabs, none of which was
 * dropped); an item Yahoo serves without one of them is dropped.
 *
 * @param id Yahoo's identifier of the item
 * @param title the headline
 * @param published when the item was published
 * @param url the item's canonical address: the publisher's own page for syndicated items
 * @param provider who published it
 * @param contentType Yahoo's content type, {@code STORY} or {@code VIDEO} at the time of writing
 * @param summary a plain-text summary
 * @param clickThroughUrl where Yahoo sends a reader who clicks the item: its own copy of a
 *     syndicated article when it hosts one, otherwise the same as {@code url}
 * @param thumbnail the item's picture in its original size
 * @param premium whether the item is reserved for Yahoo Finance premium subscribers
 */
public record NewsItem(
        String id,
        String title,
        Instant published,
        URI url,
        Provider provider,
        Optional<String> contentType,
        Optional<String> summary,
        Optional<URI> clickThroughUrl,
        Optional<Thumbnail> thumbnail,
        Optional<Boolean> premium) {

    /**
     * The publisher of a news item.
     *
     * @param name display name, e.g. {@code Reuters}
     * @param url the publisher's site
     */
    public record Provider(String name, Optional<URI> url) {}

    /**
     * A news item's picture; present only when Yahoo serves the address and both dimensions.
     *
     * @param url address of the original image
     * @param width width in pixels
     * @param height height in pixels
     */
    public record Thumbnail(URI url, int width, int height) {}
}
