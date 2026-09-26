package io.github.dimazigel.yfinance.service;

import io.github.dimazigel.yfinance.api.SearchApi;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.mapper.SearchMapper;
import io.github.dimazigel.yfinance.search.SearchResult;
import java.util.Objects;

/** Searches Yahoo Finance for quotes and news. */
public final class SearchService {

    private static final int DEFAULT_QUOTES = 8;
    private static final int DEFAULT_NEWS = 8;

    private final SearchApi api;

    public SearchService(SearchApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public SearchResult search(String query) {
        return search(query, DEFAULT_QUOTES, DEFAULT_NEWS, true);
    }

    public SearchResult search(String query, int quotesCount, int newsCount, boolean fuzzy) {
        try (var ignored = LogContext.scope("search")) {
            var response = api.search(query, quotesCount, newsCount, fuzzy);
            return SearchMapper.toSearchResult(response);
        }
    }
}
