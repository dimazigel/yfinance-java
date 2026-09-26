package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.api.YahooApis;
import io.github.dimazigel.yfinance.auth.CrumbStore;
import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.detail.CryptoDetail;
import io.github.dimazigel.yfinance.detail.EquityDetail;
import io.github.dimazigel.yfinance.detail.EtfDetail;
import io.github.dimazigel.yfinance.detail.MutualFundDetail;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.LookupType;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.fundamentals.FinancialStatement;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.http.InMemoryCookieJar;
import io.github.dimazigel.yfinance.http.RawQuoteClient;
import io.github.dimazigel.yfinance.http.YahooClientFactory;
import io.github.dimazigel.yfinance.instrument.Crypto;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.market.OptionChain;
import io.github.dimazigel.yfinance.market.PriceHistory;
import io.github.dimazigel.yfinance.search.LookupQuote;
import io.github.dimazigel.yfinance.search.SearchResult;
import io.github.dimazigel.yfinance.service.DetailService;
import io.github.dimazigel.yfinance.service.FundamentalsService;
import io.github.dimazigel.yfinance.service.HistoryService;
import io.github.dimazigel.yfinance.service.InstrumentService;
import io.github.dimazigel.yfinance.service.LookupService;
import io.github.dimazigel.yfinance.service.OptionsService;
import io.github.dimazigel.yfinance.service.SearchService;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point to the library. Holds the authenticated services, hands out {@link Ticker}s and
 * {@link Tickers}, and offers the batch-first API: N symbols in, N {@link Batch} outcomes out,
 * never throwing per symbol.
 *
 * <pre>{@code
 * try (var yf = YFinance.create()) {
 *     var aapl = yf.ticker("AAPL");
 *     Equity equity = aapl.as(Equity.class);
 *     EquityDetail detail = aapl.detail(equity);
 *     var history = aapl.history(Range.ONE_MONTH, Interval.ONE_DAY);
 * }
 * }</pre>
 *
 * <p>Instances are thread-safe and intended to be shared (treat as a singleton): they own an OkHttp
 * client with a connection pool and cached crumb. Call {@link #close()} on shutdown to release the
 * client's threads and connections.
 */
public final class YFinance implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(YFinance.class);

    final InstrumentService instruments;
    final DetailService details;
    final HistoryService history;
    final FundamentalsService fundamentals;
    final OptionsService options;
    private final SearchService search;
    private final LookupService lookup;
    private final Runnable closer;
    private final int fanOutConcurrency;
    private final AtomicBoolean closed = new AtomicBoolean();

    private YFinance(YahooApis apis, int fanOutConcurrency, Runnable closer) {
        var rawQuotes = new RawQuoteClient(apis.quote(), apis.quoteSummary());
        Clock clock = Clock.systemUTC();
        this.fanOutConcurrency = fanOutConcurrency;
        this.instruments = new InstrumentService(rawQuotes, clock);
        this.details = new DetailService(rawQuotes, clock, fanOutConcurrency);
        this.history = new HistoryService(apis.chart());
        this.fundamentals = new FundamentalsService(apis.fundamentals());
        this.options = new OptionsService(apis.options());
        this.search = new SearchService(apis.search());
        this.lookup = new LookupService(apis.lookup());
        this.closer = closer;
    }

    /** Production instance against real Yahoo Finance, performing the cookie/crumb handshake. */
    public static YFinance create() {
        return create(EndpointConfig.production());
    }

    /**
     * Instance against the given configuration, performing the cookie/crumb handshake lazily on
     * the first request. {@link EndpointConfig#fanOutConcurrency()} bounds the detail batches and
     * seeds every {@link Tickers} this instance hands out.
     */
    public static YFinance create(EndpointConfig config) {
        var cookieJar = new InMemoryCookieJar();
        var authClient = YahooClientFactory.baseClient(config, cookieJar);
        var crumbStore = new CrumbStore(authClient, config);
        var client = YahooClientFactory.apiClient(
                config, cookieJar, () -> crumbStore.tryGetCrumb().orElse(null), crumbStore::invalidate);
        LOG.atInfo().log("yfinance-java client created: hosts={}/{}, callTimeout={}, rateLimit={}, retry5xx={} attempts, fanOut={}, customizer={}",
                config.query1Base().host(), config.query2Base().host(), config.callTimeout(),
                config.adaptiveRateLimit().enabled() ? "on/" + config.adaptiveRateLimit().maxAttempts() + " attempts" : "off",
                config.transientRetry().maxAttempts(),
                config.fanOutConcurrency(),
                config.hasClientCustomizer() ? "yes" : "no");
        return new YFinance(YahooApis.create(config, client), config.fanOutConcurrency(), () -> {
            closeClient(client);
            closeClient(authClient);
            LOG.atDebug().log("yfinance-java client closed");
        });
    }

    /**
     * Instance backed by pre-built API interfaces (advanced use and testing); fan-outs run with
     * {@link Tickers#DEFAULT_CONCURRENCY}.
     */
    public static YFinance fromApis(YahooApis apis) {
        return new YFinance(Objects.requireNonNull(apis, "apis"), Tickers.DEFAULT_CONCURRENCY, () -> {});
    }

    /** Releases the underlying OkHttp client's threads and connections. Idempotent. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closer.run();
        }
    }

    public Ticker ticker(String symbol) {
        return ticker(Symbol.of(symbol));
    }

    public Ticker ticker(Symbol symbol) {
        return new Ticker(this, symbol);
    }

    /** A {@link Tickers} over {@code symbols}, fanning out with the configured concurrency. */
    public Tickers tickers(String... symbols) {
        return new Tickers(this, Arrays.stream(symbols).map(Symbol::of).toList(), fanOutConcurrency);
    }

    /** A {@link Tickers} over {@code symbols}, fanning out with the configured concurrency. */
    public Tickers tickers(List<Symbol> symbols) {
        return new Tickers(this, symbols, fanOutConcurrency);
    }

    /**
     * Every symbol classified into its asset class in one batched request (any class). A symbol
     * Yahoo does not know is {@link io.github.dimazigel.yfinance.batch.SkipReason#UNKNOWN_SYMBOL};
     * one whose class guarantee cannot be met comes back as
     * {@link io.github.dimazigel.yfinance.instrument.Unclassified} rather than being dropped.
     */
    public Batch<Instrument> instruments(Collection<Symbol> symbols) {
        return instruments.instruments(List.copyOf(symbols));
    }

    /**
     * As {@link #instruments(Collection)}, narrowed to {@code as}; symbols of another class are
     * {@link io.github.dimazigel.yfinance.batch.SkipReason#WRONG_ASSET_CLASS}, downgraded ones
     * {@link io.github.dimazigel.yfinance.batch.SkipReason#DOWNGRADED}.
     */
    public <I extends Instrument> Batch<I> instruments(Collection<Symbol> symbols, Class<I> as) {
        return instruments.instruments(List.copyOf(symbols), as);
    }

    /**
     * Equity detail for each equity: one quoteSummary request per symbol, at most
     * {@link EndpointConfig#fanOutConcurrency()} of them in flight at once.
     */
    public Batch<EquityDetail> equityDetails(Collection<Equity> equities) {
        return details.equities(List.copyOf(equities));
    }

    /** ETF detail for each ETF; see {@link #equityDetails(Collection)}. */
    public Batch<EtfDetail> etfDetails(Collection<Etf> etfs) {
        return details.etfs(List.copyOf(etfs));
    }

    /** Mutual fund detail for each fund; see {@link #equityDetails(Collection)}. */
    public Batch<MutualFundDetail> mutualFundDetails(Collection<MutualFund> funds) {
        return details.mutualFunds(List.copyOf(funds));
    }

    /** Cryptocurrency detail for each coin; see {@link #equityDetails(Collection)}. */
    public Batch<CryptoDetail> cryptoDetails(Collection<Crypto> cryptos) {
        return details.cryptos(List.copyOf(cryptos));
    }

    /** Price history per symbol, fanned out with the configured concurrency. */
    public Batch<PriceHistory> histories(Collection<Symbol> symbols, Range range, Interval interval) {
        return tickers(List.copyOf(symbols)).histories(range, interval);
    }

    /**
     * One financial statement per equity, in input order; each equity is its own proof token (see
     * {@link Ticker#statements}). The fan-out is keyed by symbol, so when two {@link Equity}
     * instances for one symbol are passed both outcomes are fetched with the first as proof — the
     * statement is the symbol's either way.
     */
    public Batch<FinancialStatement> statements(Collection<Equity> equities, StatementType type, Frequency frequency) {
        Map<Symbol, Equity> bySymbol = new HashMap<>();
        for (Equity equity : equities) {
            bySymbol.putIfAbsent(equity.symbol(), equity);
        }
        return tickers(equities.stream().map(Equity::symbol).toList())
                .fetch(ticker -> ticker.statements(Objects.requireNonNull(bySymbol.get(ticker.symbol())), type, frequency));
    }

    /** The nearest option chain per symbol; {@code Ok(Optional.empty())} for instruments without listed options. */
    public Batch<Optional<OptionChain>> options(Collection<Symbol> symbols) {
        return tickers(List.copyOf(symbols)).fetch(Ticker::options);
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
