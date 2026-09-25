package io.ziggy.yfinance;

import io.ziggy.yfinance.enums.Frequency;
import io.ziggy.yfinance.enums.Interval;
import io.ziggy.yfinance.enums.Range;
import io.ziggy.yfinance.enums.StatementType;
import io.ziggy.yfinance.model.AnalystPriceTarget;
import io.ziggy.yfinance.model.Dividend;
import io.ziggy.yfinance.model.EarningsHistoryEntry;
import io.ziggy.yfinance.model.EpsRevisionsPeriod;
import io.ziggy.yfinance.model.EpsTrendPeriod;
import io.ziggy.yfinance.model.FinancialStatement;
import io.ziggy.yfinance.model.GrowthEstimate;
import io.ziggy.yfinance.model.Holders;
import io.ziggy.yfinance.model.Info;
import io.ziggy.yfinance.model.OptionChain;
import io.ziggy.yfinance.model.PeriodEstimate;
import io.ziggy.yfinance.model.PriceHistory;
import io.ziggy.yfinance.model.Quote;
import io.ziggy.yfinance.model.SearchResult.NewsArticle;
import io.ziggy.yfinance.model.Split;
import io.ziggy.yfinance.service.HistoryRequest;
import io.ziggy.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A handle to a single instrument, exposing all per-symbol data. */
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

    public PriceHistory history(HistoryRequest request) {
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

    /** Recent news articles related to this symbol. */
    public List<NewsArticle> news() {
        return yf.search(symbol.value()).news();
    }

    private PriceHistory fullHistory() {
        return history(HistoryRequest.builder(symbol).range(Range.MAX).interval(Interval.ONE_DAY).build());
    }

    /**
     * Full company info (profile, quote, recommendations, filings, ...). For instruments
     * quoteSummary cannot describe (indices, ETFs, crypto, FX, futures) this degrades to quote-only
     * info: {@code profile()} is {@code null} and the trend lists are empty.
     */
    public Info info() {
        return yf.quote.getInfo(symbol);
    }

    /** A lightweight market quote in one request; works for every asset class. */
    public Quote quote() {
        return yf.quote.getQuote(symbol);
    }

    public FinancialStatement financials(StatementType type, Frequency frequency) {
        return yf.fundamentals.getStatement(symbol, type, frequency);
    }

    public OptionChain optionChain() {
        return yf.options.getOptionChain(symbol);
    }

    public OptionChain optionChain(Instant expiration) {
        return yf.options.getOptionChain(symbol, expiration);
    }

    public List<Instant> optionExpirations() {
        return yf.options.getExpirationDates(symbol);
    }

    public Holders holders() {
        return yf.holders.getHolders(symbol);
    }

    /** Analyst price targets, or {@code null} when Yahoo has no {@code financialData} for the symbol. */
    public @Nullable AnalystPriceTarget analystPriceTargets() {
        return yf.analysis.getAnalystPriceTargets(symbol);
    }

    public List<PeriodEstimate> earningsEstimate() {
        return yf.analysis.getEarningsEstimate(symbol);
    }

    public List<PeriodEstimate> revenueEstimate() {
        return yf.analysis.getRevenueEstimate(symbol);
    }

    public List<EarningsHistoryEntry> earningsHistory() {
        return yf.analysis.getEarningsHistory(symbol);
    }

    public List<EpsTrendPeriod> epsTrend() {
        return yf.analysis.getEpsTrend(symbol);
    }

    public List<EpsRevisionsPeriod> epsRevisions() {
        return yf.analysis.getEpsRevisions(symbol);
    }

    public List<GrowthEstimate> growthEstimates() {
        return yf.analysis.getGrowthEstimates(symbol);
    }
}
