package io.ziggy.yfinance.mapper;

import io.ziggy.yfinance.dto.search.SearchResponse;
import io.ziggy.yfinance.model.SearchResult;
import io.ziggy.yfinance.model.SearchResult.NewsArticle;
import io.ziggy.yfinance.model.SearchResult.SearchQuote;
import io.ziggy.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.Objects;

/** Maps the raw search response into {@link SearchResult}. */
public final class SearchMapper {

    private SearchMapper() {}

    public static SearchResult toSearchResult(SearchResponse response) {
        return new SearchResult(mapQuotes(response), mapNews(response));
    }

    private static List<SearchQuote> mapQuotes(SearchResponse response) {
        if (response.quotes() == null) {
            return List.of();
        }
        return response.quotes().stream()
                .filter(q -> q.symbol() != null && !q.symbol().isBlank())
                .map(q -> new SearchQuote(
                        Symbol.of(Objects.requireNonNull(q.symbol())), // filtered above
                        q.shortname(), q.longname(),
                        q.exchDisp() != null ? q.exchDisp() : q.exchange(), q.quoteType()))
                .toList();
    }

    private static List<NewsArticle> mapNews(SearchResponse response) {
        if (response.news() == null) {
            return List.of();
        }
        return response.news().stream()
                .map(n -> new NewsArticle(
                        n.uuid(), n.title(), n.publisher(), MapperSupport.uri(n.link()),
                        MapperSupport.epochSecond(n.providerPublishTime()), n.type()))
                .toList();
    }
}
