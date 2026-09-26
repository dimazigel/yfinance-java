package io.github.dimazigel.yfinance.search;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Result of a Yahoo Finance search: matching quotes and related news. */
public record SearchResult(List<SearchQuote> quotes, List<NewsArticle> news) {

    public SearchResult {
        quotes = List.copyOf(quotes);
        news = List.copyOf(news);
    }

    /** A quote match from search; only the symbol is guaranteed. */
    public record SearchQuote(
            Symbol symbol,
            Optional<String> shortName,
            Optional<String> longName,
            Optional<String> exchange,
            Optional<String> quoteType) {}

    /** A news article from search; every field is whatever Yahoo chose to include. */
    public record NewsArticle(
            Optional<String> uuid,
            Optional<String> title,
            Optional<String> publisher,
            Optional<URI> link,
            Optional<Instant> publishTime,
            Optional<String> type) {}
}
