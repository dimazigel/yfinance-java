package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.api.YahooApis;
import io.github.dimazigel.yfinance.auth.CrumbStore;
import io.github.dimazigel.yfinance.enums.LookupType;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.http.InMemoryCookieJar;
import io.github.dimazigel.yfinance.http.YahooClientFactory;
import io.github.dimazigel.yfinance.model.LookupQuote;
import io.github.dimazigel.yfinance.model.Quote;
import io.github.dimazigel.yfinance.model.SearchResult;
import io.github.dimazigel.yfinance.service.AnalysisService;
import io.github.dimazigel.yfinance.service.FundamentalsService;
import io.github.dimazigel.yfinance.service.HistoryService;
import io.github.dimazigel.yfinance.service.HoldersService;
import io.github.dimazigel.yfinance.service.LookupService;
import io.github.dimazigel.yfinance.service.OptionsService;
import io.github.dimazigel.yfinance.service.QuoteService;
import io.github.dimazigel.yfinance.service.SearchService;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import okhttp3.OkHttpClient;

/**
 * Entry point to the library. Holds the authenticated services and hands out {@link Ticker}s.
 *
 * <pre>{@code
 * try (var yf = YFinance.create()) {
 *     var aapl = yf.ticker("AAPL");
 *     var history = aapl.history(Range.ONE_MONTH, Interval.ONE_DAY);
 *     var info = aapl.info();
 * }
 * }</pre>
 *
 * <p>Instances are thread-safe and intended to be shared (treat as a singleton): they own an OkHttp
 * client with a connection pool and cached crumb. Call {@link #close()} on shutdown to release the
 * client's threads and connections.
 */
public final class YFinance implements AutoCloseable {

    final HistoryService history;
    final QuoteService quote;
    final FundamentalsService fundamentals;
    final OptionsService options;
    final HoldersService holders;
    final AnalysisService analysis;
    private final SearchService search;
    private final LookupService lookup;
    private final Runnable closer;

    private YFinance(YahooApis apis, Runnable closer) {
        this.quote = new QuoteService(apis.quoteSummary(), apis.quote());
        this.history = new HistoryService(apis.chart());
        this.fundamentals = new FundamentalsService(apis.fundamentals());
        this.options = new OptionsService(apis.options());
        this.holders = new HoldersService(quote);
        this.analysis = new AnalysisService(quote);
        this.search = new SearchService(apis.search());
        this.lookup = new LookupService(apis.lookup());
        this.closer = closer;
    }

    /** Production instance against real Yahoo Finance, performing the cookie/crumb handshake. */
    public static YFinance create() {
        return create(EndpointConfig.production());
    }

    /** Instance against the given host configuration, performing the cookie/crumb handshake. */
    public static YFinance create(EndpointConfig config) {
        var cookieJar = new InMemoryCookieJar();
        var authClient = YahooClientFactory.baseClient(config, cookieJar);
        var crumbStore = new CrumbStore(authClient, config);
        var client = YahooClientFactory.apiClient(
                config, cookieJar, () -> crumbStore.tryGetCrumb().orElse(null), crumbStore::invalidate);
        return new YFinance(YahooApis.create(config, client), () -> {
            closeClient(client);
            closeClient(authClient);
        });
    }

    /** Instance backed by pre-built API interfaces (advanced use and testing). */
    public static YFinance fromApis(YahooApis apis) {
        return new YFinance(Objects.requireNonNull(apis, "apis"), () -> {});
    }

    /** Releases the underlying OkHttp client's threads and connections. Idempotent. */
    @Override
    public void close() {
        closer.run();
    }

    public Ticker ticker(String symbol) {
        return ticker(Symbol.of(symbol));
    }

    public Ticker ticker(Symbol symbol) {
        return new Ticker(this, symbol);
    }

    public Tickers tickers(String... symbols) {
        return new Tickers(this, Arrays.stream(symbols).map(Symbol::of).toList());
    }

    public Tickers tickers(List<Symbol> symbols) {
        return new Tickers(this, symbols);
    }

    /**
     * Lightweight quotes for many symbols in one request (any asset class). Symbols Yahoo does
     * not know are absent from the map; the order given is preserved.
     */
    public Map<Symbol, Quote> quotes(String... symbols) {
        return quotes(Arrays.stream(symbols).map(Symbol::of).toList());
    }

    public Map<Symbol, Quote> quotes(List<Symbol> symbols) {
        return quote.getQuotes(symbols);
    }

    public SearchResult search(String query) {
        return search.search(query);
    }

    public List<LookupQuote> lookup(String query, LookupType type) {
        return lookup.lookup(query, type);
    }

    private static void closeClient(OkHttpClient client) {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
