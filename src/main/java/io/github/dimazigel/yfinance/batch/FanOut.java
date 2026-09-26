package io.github.dimazigel.yfinance.batch;

import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/**
 * The fan-out skeleton shared by {@code Tickers.fetch} and {@code DetailService}: one virtual
 * thread per symbol, at most {@code concurrency} of them past the semaphore at once, outcomes
 * joined back in input order (duplicates included). Library plumbing; public only because both
 * callers live in other packages.
 *
 * <p>{@code perSymbol} runs on the worker thread, so it must open its own
 * {@link io.github.dimazigel.yfinance.logging.LogContext} scope there (MDC is thread-local) and is
 * expected to turn its own exceptions into outcomes. Anything that still escapes it becomes an
 * {@link Outcome.Failed}: a {@link YFinanceException} as itself, anything else wrapped in a
 * {@link YFDataException}. An interrupt while joining restores the flag and fails that symbol.
 */
public final class FanOut {

    private FanOut() {}

    /** Runs {@code perSymbol} for every symbol with bounded concurrency; empty input starts no thread. */
    public static <T> Batch<T> run(List<Symbol> symbols, int concurrency, Function<Symbol, Outcome<T>> perSymbol) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1");
        }
        if (symbols.isEmpty()) {
            return new Batch<>(List.of());
        }
        var permits = new Semaphore(concurrency);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Outcome<T>>>(symbols.size());
            for (Symbol symbol : symbols) {
                futures.add(pool.submit(() -> {
                    permits.acquire();
                    try {
                        return perSymbol.apply(symbol);
                    } finally {
                        permits.release();
                    }
                }));
            }
            var outcomes = new ArrayList<Outcome<T>>(symbols.size());
            for (int i = 0; i < symbols.size(); i++) {
                outcomes.add(join(symbols.get(i), futures.get(i)));
            }
            return new Batch<>(outcomes);
        }
    }

    private static <T> Outcome<T> join(Symbol symbol, Future<Outcome<T>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.failed(symbol, new YFDataException("Interrupted fetching " + symbol, e));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            YFinanceException error =
                    cause instanceof YFinanceException yf ? yf : new YFDataException("Failed to fetch " + symbol, cause);
            return Outcome.failed(symbol, error);
        }
    }
}
