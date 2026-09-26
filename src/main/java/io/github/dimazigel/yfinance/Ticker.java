package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.detail.CryptoDetail;
import io.github.dimazigel.yfinance.detail.EquityDetail;
import io.github.dimazigel.yfinance.detail.EtfDetail;
import io.github.dimazigel.yfinance.detail.MutualFundDetail;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.exception.YFClassMismatchException;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.fundamentals.FinancialStatement;
import io.github.dimazigel.yfinance.instrument.Crypto;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.market.Dividend;
import io.github.dimazigel.yfinance.market.OptionChain;
import io.github.dimazigel.yfinance.market.PriceHistory;
import io.github.dimazigel.yfinance.market.Split;
import io.github.dimazigel.yfinance.search.SearchResult.NewsArticle;
import io.github.dimazigel.yfinance.service.HistoryRequest;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A handle to a single instrument: every call returns a value or throws. Batch work goes through
 * {@link Tickers} or the {@code YFinance} batch methods, which never throw per symbol.
 */
public final class Ticker {

    private final YFinance yf;
    private final Symbol symbol;

    Ticker(YFinance yf, Symbol symbol) {
        this.yf = yf;
        this.symbol = Objects.requireNonNull(symbol, "symbol");
    }

    public Symbol symbol() {
        return symbol;
    }

    /**
     * The instrument at snapshot depth, typed by asset class; {@code switch} over the sealed
     * {@link Instrument} to get at class-specific fields, or use {@link #as(Class)}.
     *
     * @throws YFDataException when Yahoo does not know the symbol
     */
    public Instrument instrument() {
        return yf.instruments.instrument(symbol);
    }

    /**
     * {@link #instrument()} as {@code type}, e.g. {@code ticker.as(Equity.class)}: the proof token
     * that {@link #detail(Equity)} and {@link #statements} take.
     *
     * @throws YFClassMismatchException when the instrument is another class (a downgraded
     *     instrument reports {@link io.github.dimazigel.yfinance.instrument.AssetClass#UNCLASSIFIED})
     */
    public <I extends Instrument> I as(Class<I> type) {
        Instrument instrument = instrument();
        if (type.isInstance(instrument)) {
            return type.cast(instrument);
        }
        throw new YFClassMismatchException(symbol, instrument.assetClass(), type);
    }

    /**
     * Equity detail (profile, statistics, financial health, analyst view, ownership).
     *
     * @throws IllegalArgumentException if {@code equity} is for a different symbol
     * @throws YFDataException when quoteSummary no longer knows the symbol or lacks a guaranteed module
     */
    public EquityDetail detail(Equity equity) {
        return yf.details.equity(proof(equity)).orElseThrow();
    }

    /** ETF detail; see {@link #detail(Equity)} for the contract. */
    public EtfDetail detail(Etf etf) {
        return yf.details.etf(proof(etf)).orElseThrow();
    }

    /** Mutual fund detail; see {@link #detail(Equity)} for the contract. */
    public MutualFundDetail detail(MutualFund fund) {
        return yf.details.mutualFund(proof(fund)).orElseThrow();
    }

    /** Cryptocurrency detail; see {@link #detail(Equity)} for the contract. */
    public CryptoDetail detail(Crypto crypto) {
        return yf.details.crypto(proof(crypto)).orElseThrow();
    }

    /**
     * Price history for an arbitrary {@link HistoryRequest} built for this ticker's symbol.
     *
     * @throws IllegalArgumentException if {@code request} was built for a different symbol; a
     *     request for MSFT sent through the AAPL ticker would otherwise silently fetch MSFT
     */
    public PriceHistory history(HistoryRequest request) {
        if (!request.symbol().equals(symbol)) {
            throw new IllegalArgumentException(
                    "HistoryRequest is for " + request.symbol() + " but this ticker is " + symbol);
        }
        return yf.history.getHistory(request);
    }

    public PriceHistory history(Range range, Interval interval) {
        return history(HistoryRequest.builder(symbol).range(range).interval(interval).build());
    }

    /** Convenience for historical backfill over an explicit {@code [start, end)} window. */
    public PriceHistory history(Instant start, Instant end, Interval interval) {
        return history(HistoryRequest.builder(symbol).period(start, end).interval(interval).build());
    }

    /** All dividends over the instrument's full history. */
    public List<Dividend> dividends() {
        return fullHistory().dividends();
    }

    /** All splits over the instrument's full history. */
    public List<Split> splits() {
        return fullHistory().splits();
    }

    /** The nearest expiration's option chain, or empty when this instrument has no listed options. */
    public Optional<OptionChain> options() {
        return yf.options.getOptionChain(symbol);
    }

    /** The option chain for a specific expiration, or empty when this instrument has no listed options. */
    public Optional<OptionChain> options(Instant expiration) {
        return yf.options.getOptionChain(symbol, expiration);
    }

    /**
     * A financial statement for this ticker's symbol. Statements are equities-only; {@code proof}
     * is the compile-time evidence that this instrument is an {@link Equity} (Yahoo's timeseries
     * endpoint returns empty series for every other class).
     *
     * @throws IllegalArgumentException if {@code proof} is for a different symbol; a proof for MSFT
     *     passed through the AAPL ticker would otherwise silently fetch MSFT's statement
     */
    public FinancialStatement statements(Equity proof, StatementType type, Frequency frequency) {
        return yf.fundamentals.getStatement(proof(proof), type, frequency);
    }

    /** Recent news articles related to this symbol. */
    public List<NewsArticle> news() {
        return yf.search(symbol.value()).news();
    }

    private PriceHistory fullHistory() {
        return history(HistoryRequest.builder(symbol).range(Range.MAX).interval(Interval.ONE_DAY).build());
    }

    /** {@code instrument} itself, once it is confirmed to be this ticker's; the proof-token guard. */
    private <I extends Instrument> I proof(I instrument) {
        if (!instrument.symbol().equals(symbol)) {
            throw new IllegalArgumentException(instrument.getClass().getSimpleName() + " proof is for "
                    + instrument.symbol() + " but this ticker is " + symbol);
        }
        return instrument;
    }
}
