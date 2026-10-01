package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.detail.CryptoDetail;
import io.github.dimazigel.yfinance.detail.EquityDetail;
import io.github.dimazigel.yfinance.detail.EtfDetail;
import io.github.dimazigel.yfinance.detail.MutualFundDetail;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.LookupType;
import io.github.dimazigel.yfinance.enums.NewsTab;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.fundamentals.FinancialStatement;
import io.github.dimazigel.yfinance.fundamentals.SharesOutstanding;
import io.github.dimazigel.yfinance.fundamentals.ValuationMeasures;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.instrument.Crypto;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.internal.api.YahooApis;
import io.github.dimazigel.yfinance.internal.auth.CrumbStore;
import io.github.dimazigel.yfinance.internal.http.RawQuoteClient;
import io.github.dimazigel.yfinance.internal.http.YahooClientFactory;
import io.github.dimazigel.yfinance.internal.service.DetailService;
import io.github.dimazigel.yfinance.internal.service.FundamentalsService;
import io.github.dimazigel.yfinance.internal.service.HistoryService;
import io.github.dimazigel.yfinance.internal.service.InstrumentService;
import io.github.dimazigel.yfinance.internal.service.LookupService;
import io.github.dimazigel.yfinance.internal.service.NewsService;
import io.github.dimazigel.yfinance.internal.service.OptionsService;
import io.github.dimazigel.yfinance.internal.service.SearchService;
import io.github.dimazigel.yfinance.market.HistoryQuery;
import io.github.dimazigel.yfinance.market.OptionChain;
import io.github.dimazigel.yfinance.market.PriceHistory;
import io.github.dimazigel.yfinance.news.NewsItem;
import io.github.dimazigel.yfinance.search.LookupQuote;
import io.github.dimazigel.yfinance.search.SearchResult;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
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

    /** {@link #sharesOutstanding(Equity)}'s default window: 18 months, mirrors yfinance's {@code get_shares_full} default. */
    private static final long SHARES_OUTSTANDING_DEFAULT_WINDOW_DAYS = 548L;

    final InstrumentService instruments;
    final DetailService details;
    final HistoryService history;
    final FundamentalsService fundamentals;
    final OptionsService options;
    private final SearchService search;
    private final LookupService lookup;
    private final NewsService news;
    private final Clock clock;
    private final Runnable closer;
    private final int fanOutConcurrency;
    private final AtomicBoolean closed = new AtomicBoolean();

    private YFinance(YahooApis apis, int fanOutConcurrency, Clock clock, Runnable closer) {
        var rawQuotes = new RawQuoteClient(apis.quote(), apis.quoteSummary());
        this.fanOutConcurrency = fanOutConcurrency;
        this.instruments = new InstrumentService(rawQuotes, clock, fanOutConcurrency);
        this.details = new DetailService(rawQuotes, clock, fanOutConcurrency);
        this.history = new HistoryService(apis.chart(), clock);
        this.fundamentals = new FundamentalsService(apis.fundamentals(), clock);
        this.options = new OptionsService(apis.options());
        this.search = new SearchService(apis.search());
        this.lookup = new LookupService(apis.lookup());
        this.news = new NewsService(apis.news());
        this.clock = clock;
        this.closer = closer;
    }

    /** Production instance against real Yahoo Finance, performing the cookie/crumb handshake. */
    public static YFinance create() {
        return create(EndpointConfig.production());
    }

    /**
     * Instance against the given configuration, performing the cookie/crumb handshake lazily on
     * the first request. {@link EndpointConfig#fanOutConcurrency()} bounds the detail batches and
     * seeds every {@link Tickers} this instance hands out; cookies live in
     * {@link EndpointConfig#cookieJar()} (shared by every instance created from the same config)
     * and {@link EndpointConfig#clock()} is the services' source of "now".
     */
    public static YFinance create(EndpointConfig config) {
        var cookieJar = config.cookieJar();
        var limiter = YahooClientFactory.newRateLimiter(config);
        var dispatcher = YahooClientFactory.newDispatcher(config);
        var pool = new ConnectionPool();
        var authClient = YahooClientFactory.baseClient(config, cookieJar, limiter, dispatcher, pool);
        var crumbStore = new CrumbStore(authClient, config);
        var client = YahooClientFactory.apiClient(
                config, cookieJar, () -> crumbStore.tryGetCrumb().orElse(null), crumbStore::invalidate,
                limiter, dispatcher, pool);
        LOG.atInfo().log("yfinance-java client created: hosts={}/{}, callTimeout={}, rateLimit={}, retry5xx={} attempts, fanOut={}, customizer={}",
                config.query1Base().host(), config.query2Base().host(), config.callTimeout(),
                config.adaptiveRateLimit().enabled() ? "on/" + config.adaptiveRateLimit().maxAttempts() + " attempts" : "off",
                config.transientRetry().maxAttempts(),
                config.fanOutConcurrency(),
                config.hasClientCustomizer() ? "yes" : "no");
        return new YFinance(YahooApis.create(config, client), config.fanOutConcurrency(), config.clock(), () -> {
            releaseOwned(client, dispatcher, pool);
            releaseOwned(authClient, dispatcher, pool);
            LOG.atDebug().log("yfinance-java client closed");
        });
    }

    /**
     * Instance backed by pre-built API interfaces, skipping the handshake; fan-outs run with
     * {@link Tickers#DEFAULT_CONCURRENCY}. Package-private: {@code YahooApis} is internal, so this
     * is a seam for the library's own tests, not API.
     */
    static YFinance fromApis(YahooApis apis) {
        return new YFinance(Objects.requireNonNull(apis, "apis"), Tickers.DEFAULT_CONCURRENCY, Clock.systemUTC(), () -> {});
    }

    /**
     * Releases the OkHttp dispatcher and connection pool this instance created (both clients share
     * one of each). A dispatcher or pool installed through
     * {@link EndpointConfig#clientCustomizer()} is the caller's — typically shared with other
     * clients — and is left running. Idempotent.
     */
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
     * Equity detail for one equity in one quoteSummary request: the instrument in hand is the
     * proof of its class, so nothing has to be matched against a {@link Ticker}. This is the
     * single-instrument form of {@link #equityDetails(Collection)}.
     *
     * @param equity the equity, e.g. from {@link #instruments(Collection, Class)} or {@link Ticker#as}
     * @return the detail record
     * @throws io.github.dimazigel.yfinance.exception.YFSkippedException when quoteSummary no longer
     *     knows the symbol ({@code UNKNOWN_SYMBOL}) or lacks a guaranteed module ({@code MODULE_ABSENT})
     */
    public EquityDetail detail(Equity equity) {
        return details.equity(equity).orElseThrow();
    }

    /**
     * ETF detail for one ETF in one quoteSummary request; see {@link #detail(Equity)} for the contract.
     *
     * @param etf the ETF
     * @return the detail record
     */
    public EtfDetail detail(Etf etf) {
        return details.etf(etf).orElseThrow();
    }

    /**
     * Mutual fund detail for one fund in one quoteSummary request; see {@link #detail(Equity)} for the contract.
     *
     * @param fund the mutual fund
     * @return the detail record
     */
    public MutualFundDetail detail(MutualFund fund) {
        return details.mutualFund(fund).orElseThrow();
    }

    /**
     * Cryptocurrency detail for one coin in one quoteSummary request; see {@link #detail(Equity)} for the contract.
     *
     * @param crypto the cryptocurrency
     * @return the detail record
     */
    public CryptoDetail detail(Crypto crypto) {
        return details.crypto(crypto).orElseThrow();
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
    public Batch<PriceHistory> histories(Collection<Symbol> symbols, HistoryQuery query) {
        return tickers(List.copyOf(symbols)).histories(query);
    }

    /** Price history per symbol, fanned out with the configured concurrency. */
    public Batch<PriceHistory> histories(Collection<Symbol> symbols, Range range, Interval interval) {
        return histories(symbols, HistoryQuery.range(range, interval));
    }

    /**
     * One financial statement for one equity in one timeseries request — a single statement's keys
     * always fit within the ≤ 150-key chunk limit (see {@link #statements(Equity, Set, Set)} for the
     * multi-statement form, which may need several). Statements are equities-only (Yahoo's
     * timeseries endpoint returns empty series for every other class), and the {@link Equity} in
     * hand is the proof; see {@link Ticker#statements(Equity, StatementType, Frequency)}.
     *
     * @param equity the equity whose statement to fetch
     * @param type income statement, balance sheet or cash flow
     * @param frequency annual, quarterly or trailing twelve months
     * @return the statement
     * @throws IllegalArgumentException for {@link Frequency#TRAILING} with
     *     {@link StatementType#BALANCE_SHEET}, which Yahoo does not publish
     */
    public FinancialStatement statements(Equity equity, StatementType type, Frequency frequency) {
        return fundamentals.getStatement(equity, type, frequency);
    }

    /**
     * Several statements for one equity in as few timeseries requests as the key count allows
     * (≤ 150 keys each; a single statement is one request, the full 3×3 form is seven): every
     * requested type at every requested frequency (the trailing balance sheet, which Yahoo does not
     * publish, is skipped rather than an error when other pairs remain). Several statements this way
     * cost ⌈keys/150⌉ requests instead of one per pair.
     *
     * @param equity the equity whose statements to fetch
     * @param types the statements wanted; not empty
     * @param frequencies the frequencies wanted; not empty
     * @return statement type → frequency → statement, unmodifiable, one entry per servable pair
     * @throws IllegalArgumentException when either set is empty, or when the only pair is the
     *     trailing balance sheet
     */
    public Map<StatementType, Map<Frequency, FinancialStatement>> statements(
            Equity equity, Set<StatementType> types, Set<Frequency> frequencies) {
        return fundamentals.getStatements(equity, types, frequencies);
    }

    /**
     * {@link #statements(Equity, Set, Set)} for each equity: as few timeseries requests per equity
     * as the key count allows (≤ 150 keys each; a single statement is one request, the full 3×3 form
     * is seven), fanned out with the configured concurrency, in input order (duplicates preserved;
     * see {@link #statements(Collection, StatementType, Frequency)} for the proof handling).
     *
     * @param equities the equities
     * @param types the statements wanted; not empty
     * @param frequencies the frequencies wanted; not empty
     * @return one outcome per equity, whose value is statement type → frequency → statement
     * @throws IllegalArgumentException before any request when either set is empty or the only
     *     pair is the trailing balance sheet — a programming error is one exception, never N
     *     {@code Failed} outcomes
     */
    public Batch<Map<StatementType, Map<Frequency, FinancialStatement>>> statements(
            Collection<Equity> equities, Set<StatementType> types, Set<Frequency> frequencies) {
        FundamentalsService.requireServablePairs(types, frequencies);
        Map<Symbol, Equity> bySymbol = new HashMap<>();
        for (Equity equity : equities) {
            bySymbol.putIfAbsent(equity.symbol(), equity);
        }
        return tickers(equities.stream().map(Equity::symbol).toList())
                .fetch(ticker -> ticker.statements(Objects.requireNonNull(bySymbol.get(ticker.symbol())), types, frequencies));
    }

    /**
     * One financial statement per equity, in input order; each equity is its own proof token (see
     * {@link Ticker#statements}). The fan-out is keyed by symbol, so when two {@link Equity}
     * instances for one symbol are passed both outcomes are fetched with the first as proof — the
     * statement is the symbol's either way.
     *
     * @throws IllegalArgumentException before any request for {@link Frequency#TRAILING} with
     *     {@link StatementType#BALANCE_SHEET}, which Yahoo does not publish
     */
    public Batch<FinancialStatement> statements(Collection<Equity> equities, StatementType type, Frequency frequency) {
        FundamentalsService.requireServablePairs(Set.of(type), Set.of(frequency));
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

    /**
     * Historical shares-outstanding reports for one equity over {@code [start, end]} in one
     * request (batch E/1, Python yfinance's {@code get_shares_full}).
     *
     * @param equity the equity whose share counts to fetch
     * @param start the window start (inclusive)
     * @param end the window end (inclusive)
     * @return the reports in wire order; empty when Yahoo has no history for the symbol
     * @throws IllegalArgumentException if {@code start} is not before {@code end}
     */
    public List<SharesOutstanding> sharesOutstanding(Equity equity, Instant start, Instant end) {
        return fundamentals.getSharesOutstanding(equity, start, end);
    }

    /**
     * {@link #sharesOutstanding(Equity, Instant, Instant)} over the default window
     * {@code [now - 548 days, now]} (18 months, mirrors yfinance's {@code get_shares_full} default),
     * "now" taken from {@link EndpointConfig#clock()}.
     */
    public List<SharesOutstanding> sharesOutstanding(Equity equity) {
        Instant end = clock.instant();
        return sharesOutstanding(equity, end.minus(SHARES_OUTSTANDING_DEFAULT_WINDOW_DAYS, ChronoUnit.DAYS), end);
    }

    /**
     * One equity's valuation measures at each recent period end, oldest first, in one request
     * (Python yfinance's {@code Ticker.valuation}): market cap, enterprise value, trailing and
     * forward P/E, PEG, price/sales, price/book, EV/revenue and EV/EBITDA.
     * {@link Frequency#QUARTERLY} gives the latest five quarter ends, {@link Frequency#ANNUAL} the
     * latest four fiscal year ends; Yahoo serves no more per request.
     *
     * @param equity the equity whose valuation history to fetch
     * @param frequency {@link Frequency#QUARTERLY} or {@link Frequency#ANNUAL}
     * @return one row per period end that has at least one measure; empty when Yahoo has none
     * @throws IllegalArgumentException for {@link Frequency#TRAILING}, which Yahoo does not serve
     *     as a history by period; the current values are on {@link Equity} and {@link EquityDetail}
     */
    public List<ValuationMeasures> valuationHistory(Equity equity, Frequency frequency) {
        return fundamentals.getValuationHistory(equity, frequency);
    }

    /** {@link #valuationHistory(Equity, Frequency)} by quarter, the table Yahoo's statistics page shows. */
    public List<ValuationMeasures> valuationHistory(Equity equity) {
        return valuationHistory(equity, Frequency.QUARTERLY);
    }

    /**
     * One symbol's news stream in one request (Python yfinance's {@code Ticker.get_news}): up to
     * {@code count} items of {@code tab}, in Yahoo's order.
     *
     * @return the items; empty when Yahoo has none for the symbol, an unknown symbol included
     * @throws IllegalArgumentException if {@code count} is less than 1
     */
    public List<NewsItem> news(Symbol symbol, NewsTab tab, int count) {
        return news.getNews(symbol, tab, count);
    }

    public SearchResult search(String query) {
        return search.search(query);
    }

    public List<LookupQuote> lookup(String query, LookupType type) {
        return lookup.lookup(query, type);
    }

    /** Shuts down only what the library created: a customizer may have swapped in the caller's own. */
    private static void releaseOwned(OkHttpClient client, Dispatcher ownDispatcher, ConnectionPool ownPool) {
        if (client.dispatcher() == ownDispatcher) {
            ownDispatcher.executorService().shutdown();
        }
        if (client.connectionPool() == ownPool) {
            ownPool.evictAll();
        }
    }
}
