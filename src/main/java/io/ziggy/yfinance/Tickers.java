package io.ziggy.yfinance;

import io.ziggy.yfinance.enums.Interval;
import io.ziggy.yfinance.enums.Range;
import io.ziggy.yfinance.exception.YFDataException;
import io.ziggy.yfinance.exception.YFinanceException;
import io.ziggy.yfinance.model.Info;
import io.ziggy.yfinance.model.PriceHistory;
import io.ziggy.yfinance.service.HistoryRequest;
import io.ziggy.yfinance.valueobject.Symbol;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A group of tickers. Queries are fanned out across symbols with bounded concurrency, and each
 * symbol yields an independent {@link Result} — one symbol failing never discards the others, which
 * is what an ingestion pipeline needs.
 */
public final class Tickers {

    private static final int DEFAULT_CONCURRENCY = 4;

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

    public Map<Symbol, Result<Info>> infos() {
        return fanOut(yf.quote::getInfo);
    }

    public Map<Symbol, Result<PriceHistory>> histories(Range range, Interval interval) {
        return fanOut(symbol -> yf.history.getHistory(
                HistoryRequest.builder(symbol).range(range).interval(interval).build()));
    }

    private <T> Map<Symbol, Result<T>> fanOut(Function<Symbol, T> fetch) {
        var permits = new Semaphore(concurrency);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<Symbol, Future<Result<T>>> futures = new LinkedHashMap<>();
            for (Symbol symbol : symbols) {
                futures.put(symbol, pool.submit(() -> {
                    permits.acquire();
                    try {
                        return runOne(symbol, fetch);
                    } finally {
                        permits.release();
                    }
                }));
            }
            Map<Symbol, Result<T>> results = new LinkedHashMap<>();
            futures.forEach((symbol, future) -> results.put(symbol, join(symbol, future)));
            return results;
        }
    }

    private static <T> Result<T> runOne(Symbol symbol, Function<Symbol, T> fetch) {
        try {
            return Result.success(symbol, fetch.apply(symbol));
        } catch (YFinanceException e) {
            return Result.failure(symbol, e);
        } catch (RuntimeException e) {
            return Result.failure(symbol, new YFDataException("Failed to fetch " + symbol, e));
        }
    }

    private static <T> Result<T> join(Symbol symbol, Future<Result<T>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure(symbol, new YFDataException("Interrupted fetching " + symbol, e));
        } catch (ExecutionException e) {
            return Result.failure(symbol, new YFDataException("Failed to fetch " + symbol, e.getCause()));
        }
    }

    /**
     * The outcome of fetching one symbol: either a {@code value} (success) or an {@code error}.
     *
     * @param <T> the payload type
     */
    public record Result<T>(Symbol symbol, @Nullable T value, @Nullable YFinanceException error) {

        public static <T> Result<T> success(Symbol symbol, T value) {
            return new Result<>(symbol, Objects.requireNonNull(value, "value"), null);
        }

        public static <T> Result<T> failure(Symbol symbol, YFinanceException error) {
            return new Result<>(symbol, null, Objects.requireNonNull(error, "error"));
        }

        public boolean isSuccess() {
            return error == null;
        }

        /** Returns the value on success, or rethrows the captured error. */
        public T orElseThrow() {
            if (error != null) {
                throw error;
            }
            return Objects.requireNonNull(value, "value"); // success results always carry a value
        }
    }
}
