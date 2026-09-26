package io.github.dimazigel.yfinance.mapper;

import io.github.dimazigel.yfinance.dto.search.SearchResponse;
import io.github.dimazigel.yfinance.search.SearchResult;
import io.github.dimazigel.yfinance.search.SearchResult.NewsArticle;
import io.github.dimazigel.yfinance.search.SearchResult.SearchQuote;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
                        Optional.ofNullable(q.shortname()),
                        Optional.ofNullable(q.longname()),
                        Optional.ofNullable(q.exchDisp() != null ? q.exchDisp() : q.exchange()),
                        Optional.ofNullable(q.quoteType())))
                .toList();
    }

    private static List<NewsArticle> mapNews(SearchResponse response) {
        if (response.news() == null) {
            return List.of();
        }
        return response.news().stream()
                .map(n -> new NewsArticle(
                        Optional.ofNullable(n.uuid()),
                        Optional.ofNullable(n.title()),
                        Optional.ofNullable(n.publisher()),
                        Optional.ofNullable(MapperSupport.uri(n.link())),
                        Optional.ofNullable(MapperSupport.epochSecond(n.providerPublishTime())),
                        Optional.ofNullable(n.type())))
                .toList();
    }
}
