package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.market.PriceHistory;
import io.github.dimazigel.yfinance.model.Info;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A group of tickers. Queries are fanned out across symbols with bounded concurrency, and each
 * symbol yields an independent {@link Result} — one symbol failing never discards the others, which
 * is what an ingestion pipeline needs.
 *
 * <p>{@link #fetch(Function)} fans out any {@link Ticker} call; {@link #infos()} and
 * {@link #histories(Range, Interval)} are shorthands for the two most common ones.
 */
public final class Tickers {

    private static final Logger LOG = LoggerFactory.getLogger(Tickers.class);
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

    /**
     * Applies {@code fetcher} to every symbol's {@link Ticker} concurrently, e.g.
     * {@code tickers.fetch(Ticker::optionChain)} or {@code tickers.fetch(t -> t.financials(INCOME, ANNUAL))}.
     *
     * <p>A fetcher that throws yields a {@link Result.Failure} for that symbol only; a fetcher that
     * returns {@code null} (some calls are {@code @Nullable}, e.g. {@link Ticker#analystPriceTargets()}
     * for an index) yields a failure whose message says no data was returned.
     */
    public <T> Map<Symbol, Result<T>> fetch(Function<? super Ticker, ? extends @Nullable T> fetcher) {
        Objects.requireNonNull(fetcher, "fetcher");
        return fanOut(symbol -> fetcher.apply(new Ticker(yf, symbol)));
    }

    public Map<Symbol, Result<Info>> infos() {
        return fetch(Ticker::info);
    }

    public Map<Symbol, Result<PriceHistory>> histories(Range range, Interval interval) {
        return fetch(ticker -> ticker.history(range, interval));
    }

    private <T> Map<Symbol, Result<T>> fanOut(Function<Symbol, ? extends @Nullable T> fetch) {
        long startedAt = System.nanoTime();
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
            summarize(results, (System.nanoTime() - startedAt) / 1_000_000);
            return results;
        }
    }

    /** One INFO line per fan-out; each failure at DEBUG (the caller holds the exception itself). */
    private static <T> void summarize(Map<Symbol, Result<T>> results, long durationMs) {
        int failed = 0;
        for (Result<T> result : results.values()) {
            if (result instanceof Result.Failure<T> failure) {
                failed++;
                LOG.atDebug().log("{} failed: {}: {}", failure.symbol(),
                        failure.error().getClass().getSimpleName(), failure.error().getMessage());
            }
        }
        LOG.atInfo()
                .addKeyValue("symbols", results.size())
                .addKeyValue("ok", results.size() - failed)
                .addKeyValue("failed", failed)
                .addKeyValue("durationMs", durationMs)
                .log("Fetched {} symbols: {} ok, {} failed in {} ms", results.size(), results.size() - failed, failed, durationMs);
    }

    private static <T> Result<T> runOne(Symbol symbol, Function<Symbol, ? extends @Nullable T> fetch) {
        try {
            T value = fetch.apply(symbol);
            if (value == null) {
                return Result.failure(symbol, new YFDataException(symbol + ": no data returned"));
            }
            return Result.success(symbol, value);
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
     * The outcome of fetching one symbol: a {@link Success} carrying the value or a {@link Failure}
     * carrying the exception. Sealed, so a {@code switch} over it is exhaustive without a default:
     *
     * <pre>{@code
     * switch (result) {
     *     case Tickers.Result.Success<Info> ok -> store(ok.symbol(), ok.value());
     *     case Tickers.Result.Failure<Info> failed -> log.warn("{}: {}", failed.symbol(), failed.error().getMessage());
     * }
     * }</pre>
     *
     * @param <T> the payload type
     */
    public sealed interface Result<T> permits Result.Success, Result.Failure {

        Symbol symbol();

        /** Returns the value on success, or rethrows the captured error. */
        T orElseThrow();

        default boolean isSuccess() {
            return this instanceof Success<T>;
        }

        /** The value on success, empty on failure. */
        default Optional<T> toOptional() {
            return this instanceof Success<T> ok ? Optional.of(ok.value()) : Optional.empty();
        }

        static <T> Result<T> success(Symbol symbol, T value) {
            return new Success<>(symbol, value);
        }

        static <T> Result<T> failure(Symbol symbol, YFinanceException error) {
            return new Failure<>(symbol, error);
        }

        /** A symbol whose fetch succeeded. */
        record Success<T>(Symbol symbol, T value) implements Result<T> {

            public Success {
                Objects.requireNonNull(symbol, "symbol");
                Objects.requireNonNull(value, "value");
            }

            @Override
            public T orElseThrow() {
                return value;
            }
        }

        /** A symbol whose fetch failed; the batch carried on without it. */
        record Failure<T>(Symbol symbol, YFinanceException error) implements Result<T> {

            public Failure {
                Objects.requireNonNull(symbol, "symbol");
                Objects.requireNonNull(error, "error");
            }

            @Override
            public T orElseThrow() {
                throw error;
            }
        }
    }
}
