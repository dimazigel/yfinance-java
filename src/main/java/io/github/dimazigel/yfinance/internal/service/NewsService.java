package io.github.dimazigel.yfinance.internal.service;

import io.github.dimazigel.yfinance.enums.NewsTab;
import io.github.dimazigel.yfinance.internal.api.NewsApi;
import io.github.dimazigel.yfinance.internal.dto.news.NewsRequest;
import io.github.dimazigel.yfinance.internal.mapper.NewsMapper;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.news.NewsItem;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.Objects;

/** A symbol's news stream ({@code Ticker.news} parity): one request per call. */
public final class NewsService {

    private final NewsApi api;

    public NewsService(NewsApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    /**
     * Up to {@code count} items of {@code symbol}'s stream on {@code tab}, in Yahoo's order; empty
     * when Yahoo has nothing for the symbol (an unknown symbol included).
     *
     * @throws IllegalArgumentException if {@code count} is less than 1
     */
    public List<NewsItem> getNews(Symbol symbol, NewsTab tab, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1, was " + count);
        }
        try (var ignored = LogContext.scope("news", symbol)) {
            var body = new NewsRequest(new NewsRequest.ServiceConfig(count, List.of(symbol.value())));
            return NewsMapper.toItems(api.news(tab.wireValue(), body));
        }
    }
}
