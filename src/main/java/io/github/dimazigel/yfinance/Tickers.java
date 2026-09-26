package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.market.PriceHistory;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
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
     * order. Outcomes are {@link Outcome.Ok} or {@link Outcome.Failed} only: a fetcher that throws
     * a {@link YFinanceException} fails that symbol with it, any other exception or a {@code null}
     * result is wrapped in a {@link YFDataException}.
     */
    public <T> Batch<T> fetch(Function<? super Ticker, T> fetcher) {
        Objects.requireNonNull(fetcher, "fetcher");
        if (symbols.isEmpty()) {
            return new Batch<>(List.of());
        }
        long startedAt = System.nanoTime();
        try (var ignored = LogContext.scope("fetch", symbols)) {
            var permits = new Semaphore(concurrency);
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new ArrayList<Future<Outcome<T>>>(symbols.size());
                for (Symbol symbol : symbols) {
                    futures.add(pool.submit(() -> {
                        permits.acquire();
                        try {
                            return fetchOne(symbol, fetcher);
                        } finally {
                            permits.release();
                        }
                    }));
                }
                var outcomes = new ArrayList<Outcome<T>>(symbols.size());
                for (int i = 0; i < symbols.size(); i++) {
                    outcomes.add(join(symbols.get(i), futures.get(i)));
                }
                var batch = new Batch<>(outcomes);
                summarize(batch, (System.nanoTime() - startedAt) / 1_000_000);
                return batch;
            }
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
     * Runs on the fan-out worker thread, so the per-symbol {@link LogContext} scope is opened here:
     * MDC is thread-local, and both the fetcher's HTTP calls and the failure line happen on this
     * thread, not on the one that opened the batch scope.
     */
    private <T> Outcome<T> fetchOne(Symbol symbol, Function<? super Ticker, T> fetcher) {
        try (var ignored = LogContext.scope("fetch", symbol)) {
            Outcome<T> outcome;
            try {
                T value = fetcher.apply(new Ticker(yf, symbol));
                outcome = value == null
                        ? Outcome.failed(symbol, new YFDataException(symbol + ": no data returned"))
                        : Outcome.ok(symbol, value);
            } catch (YFinanceException e) {
                outcome = Outcome.failed(symbol, e);
            } catch (RuntimeException e) {
                outcome = Outcome.failed(symbol, new YFDataException("Failed to fetch " + symbol, e));
            }
            if (outcome instanceof Outcome.Failed<T> failed) {
                LOG.atDebug().log("{} failed: {}: {}", symbol,
                        failed.error().getClass().getSimpleName(), failed.error().getMessage());
            }
            return outcome;
        }
    }

    private static <T> Outcome<T> join(Symbol symbol, Future<Outcome<T>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.failed(symbol, new YFDataException("Interrupted fetching " + symbol, e));
        } catch (ExecutionException e) {
            return Outcome.failed(symbol, new YFDataException("Failed to fetch " + symbol, e.getCause()));
        }
    }

    /** One INFO line per fan-out; each failure was already logged at DEBUG on its worker. */
    private static <T> void summarize(Batch<T> batch, long durationMs) {
        int failed = batch.failed().size();
        int ok = batch.size() - failed;
        LOG.atInfo()
                .addKeyValue("symbols", batch.size())
                .addKeyValue("ok", ok)
                .addKeyValue("failed", failed)
                .addKeyValue("durationMs", durationMs)
                .log("Fetched {} symbols: {} ok, {} failed in {} ms", batch.size(), ok, failed, durationMs);
    }
}
