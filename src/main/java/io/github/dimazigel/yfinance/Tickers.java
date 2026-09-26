package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.batch.FanOut;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.exception.YFClassMismatchException;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFSkippedException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.market.PriceHistory;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A group of tickers. {@link #fetch(Function)} fans any {@link Ticker} call out across the symbols
 * with bounded concurrency and collects one {@link Outcome} per symbol into a {@link Batch}: one
 * symbol failing never discards the others, which is what an ingestion pipeline needs.
 * {@link #instruments()} is not a fan-out: it classifies every symbol in one batched request.
 */
public final class Tickers {

    private static final Logger LOG = LoggerFactory.getLogger(Tickers.class);
    static final int DEFAULT_CONCURRENCY = 4;

    private final YFinance yf;
    private final List<Symbol> symbols;
    private final int concurrency;

    Tickers(YFinance yf, List<Symbol> symbols) {
        this(yf, symbols, DEFAULT_CONCURRENCY);
    }

    private Tickers(YFinance yf, List<Symbol> symbols, int concurrency) {
        this.yf = yf;
        this.symbols = List.copyOf(Objects.requireNonNull(symbols, "symbols"));
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1");
        }
        this.concurrency = concurrency;
    }

    /** Returns a copy that fans out with at most {@code concurrency} simultaneous requests. */
    public Tickers withConcurrency(int concurrency) {
        return new Tickers(yf, symbols, concurrency);
    }

    public List<Symbol> symbols() {
        return symbols;
    }

    public Ticker ticker(Symbol symbol) {
        return new Ticker(yf, symbol);
    }

    /**
     * Applies {@code fetcher} to every symbol's {@link Ticker} concurrently, e.g.
     * {@code tickers.fetch(Ticker::options)} or {@code tickers.fetch(Ticker::dividends)}, in input
     * order, classifying each result the way the {@code YFinance} batch calls do:
     *
     * <ul>
     *   <li>{@link Outcome.Ok}: the fetcher returned a value.
     *   <li>{@link Outcome.Skipped}: the fetcher threw what a single {@link Ticker} throws for a
     *       non-answer — a {@link YFSkippedException} (unknown symbol, guaranteed module absent, …)
     *       keeps its {@link SkipReason}; a {@link YFClassMismatchException} from {@link Ticker#as}
     *       becomes {@link SkipReason#WRONG_ASSET_CLASS}, or {@link SkipReason#DOWNGRADED} when the
     *       instrument is {@link AssetClass#UNCLASSIFIED}. Not worth retrying.
     *   <li>{@link Outcome.Failed}: any other {@link YFinanceException} as itself; any other
     *       exception or a {@code null} result wrapped in a {@link YFDataException}. Retryable.
     * </ul>
     */
    public <T> Batch<T> fetch(Function<? super Ticker, T> fetcher) {
        Objects.requireNonNull(fetcher, "fetcher");
        if (symbols.isEmpty()) {
            return new Batch<>(List.of());
        }
        long startedAt = System.nanoTime();
        try (var ignored = LogContext.scope("fetch", symbols)) {
            Batch<T> batch = FanOut.run(symbols, concurrency, symbol -> fetchOne(symbol, fetcher));
            summarize(batch, (System.nanoTime() - startedAt) / 1_000_000);
            return batch;
        }
    }

    /** Every symbol classified in one batched request; see {@code YFinance.instruments(Collection)}. */
    public Batch<Instrument> instruments() {
        return yf.instruments(symbols);
    }

    public Batch<PriceHistory> histories(Range range, Interval interval) {
        return fetch(ticker -> ticker.history(range, interval));
    }

    /**
     * Runs on the {@link FanOut} worker thread, so the per-symbol {@link LogContext} scope is opened
     * here: MDC is thread-local, and both the fetcher's HTTP calls and the failure line happen on
     * this thread, not on the one that opened the batch scope.
     */
    private <T> Outcome<T> fetchOne(Symbol symbol, Function<? super Ticker, T> fetcher) {
        try (var ignored = LogContext.scope("fetch", symbol)) {
            Outcome<T> outcome;
            try {
                T value = fetcher.apply(new Ticker(yf, symbol));
                outcome = value == null
                        ? Outcome.failed(symbol, new YFDataException(symbol + ": no data returned"))
                        : Outcome.ok(symbol, value);
            } catch (YFSkippedException e) {
                outcome = Outcome.skipped(symbol, e.reason(), e.field());
            } catch (YFClassMismatchException e) {
                SkipReason reason = e.actual() == AssetClass.UNCLASSIFIED ? SkipReason.DOWNGRADED : SkipReason.WRONG_ASSET_CLASS;
                outcome = Outcome.skipped(symbol, reason, e.actual().name());
            } catch (YFinanceException e) {
                outcome = Outcome.failed(symbol, e);
            } catch (RuntimeException e) {
                outcome = Outcome.failed(symbol, new YFDataException("Failed to fetch " + symbol, e));
            }
            switch (outcome) {
                case Outcome.Failed<T> failed -> LOG.atDebug().log("{} failed: {}: {}", symbol,
                        failed.error().getClass().getSimpleName(), failed.error().getMessage());
                case Outcome.Skipped<T> skipped -> LOG.atDebug().addKeyValue("reason", skipped.reason())
                        .log("{} skipped: {} ({})", symbol, skipped.reason(), skipped.detail());
                case Outcome.Ok<T> ok -> { }
            }
            return outcome;
        }
    }

    /** One INFO line per fan-out; each skip and failure was already logged at DEBUG on its worker. */
    private static <T> void summarize(Batch<T> batch, long durationMs) {
        int skipped = batch.skipped().size();
        int failed = batch.failed().size();
        int ok = batch.size() - skipped - failed;
        LOG.atInfo()
                .addKeyValue("symbols", batch.size())
                .addKeyValue("ok", ok)
                .addKeyValue("skipped", skipped)
                .addKeyValue("failed", failed)
                .addKeyValue("durationMs", durationMs)
                .log("Fetched {} symbols: {} ok, {} skipped, {} failed in {} ms", batch.size(), ok, skipped, failed, durationMs);
    }
}
