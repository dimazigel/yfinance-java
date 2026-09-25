package io.github.dimazigel.yfinance.model;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Result of a Yahoo Finance search: matching quotes and related news. */
public record SearchResult(List<SearchQuote> quotes, List<NewsArticle> news) {

    public SearchResult {
        quotes = quotes == null ? List.of() : List.copyOf(quotes);
        news = news == null ? List.of() : List.copyOf(news);
    }

    /** A quote match from search. */
    public record SearchQuote(
            Symbol symbol,
            @Nullable String shortName,
            @Nullable String longName,
            @Nullable String exchange,
            @Nullable String quoteType) {}

    /** A news article from search. */
    public record NewsArticle(
            @Nullable String uuid,
            @Nullable String title,
            @Nullable String publisher,
            @Nullable URI link,
            @Nullable Instant publishTime,
            @Nullable String type) {}
}
