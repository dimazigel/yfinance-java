package io.github.dimazigel.yfinance.batch;

import io.github.dimazigel.yfinance.exception.YFSkippedException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * N symbols in, N outcomes out, in input order. Never throws per symbol. Immutable and
 * thread-safe; iterating it visits every {@link Outcome} in input order.
 *
 * @param outcomes one outcome per input symbol, in input order (duplicates preserved)
 * @param <T> the value type of an {@link Outcome.Ok}
 */
public record Batch<T>(List<Outcome<T>> outcomes) implements Iterable<Outcome<T>> {

    public Batch {
        outcomes = List.copyOf(outcomes);
    }

    /** The number of outcomes, equal to the number of input symbols. */
    public int size() {
        return outcomes.size();
    }

    @Override
    public Iterator<Outcome<T>> iterator() {
        return outcomes.iterator();
    }

    /** The outcomes as a sequential stream, in input order. */
    public Stream<Outcome<T>> stream() {
        return outcomes.stream();
    }

    /** Values of the {@link Outcome.Ok} outcomes, in order. */
    public List<T> values() {
        return outcomes.stream().flatMap(o -> o.optional().stream()).toList();
    }

    /** The successful outcomes, in input order; each still carries its symbol. */
    public List<Outcome.Ok<T>> ok() {
        var result = new ArrayList<Outcome.Ok<T>>();
        for (var outcome : outcomes) {
            if (outcome instanceof Outcome.Ok<T> ok) {
                result.add(ok);
            }
        }
        return List.copyOf(result);
    }

    /** The skipped outcomes, in input order. */
    public List<Outcome.Skipped<T>> skipped() {
        var result = new ArrayList<Outcome.Skipped<T>>();
        for (var outcome : outcomes) {
            if (outcome instanceof Outcome.Skipped<T> s) {
                result.add(s);
            }
        }
        return List.copyOf(result);
    }

    /** The failed outcomes, in input order. */
    public List<Outcome.Failed<T>> failed() {
        var result = new ArrayList<Outcome.Failed<T>>();
        for (var outcome : outcomes) {
            if (outcome instanceof Outcome.Failed<T> f) {
                result.add(f);
            }
        }
        return List.copyOf(result);
    }

    /** Whether every outcome is an {@link Outcome.Ok}; vacuously true for an empty batch. */
    public boolean allOk() {
        for (var outcome : outcomes) {
            if (!(outcome instanceof Outcome.Ok<T>)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The {@link Outcome.Ok} values keyed by symbol, in input order. Skipped and failed symbols
     * are absent; when a symbol occurs more than once, the first {@code Ok} wins. Unmodifiable.
     */
    public Map<Symbol, T> toMap() {
        var map = new LinkedHashMap<Symbol, T>();
        for (var outcome : outcomes) {
            if (outcome instanceof Outcome.Ok<T> ok) {
                map.putIfAbsent(ok.symbol(), ok.value());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Every value, or the first non-{@code Ok} outcome's exception: what {@link Outcome#orElseThrow()}
     * throws for it — a {@link YFSkippedException} for a skip, the original error for a failure.
     * For scripts and tests that want the batch to be all-or-nothing.
     *
     * @return {@link #values()} when {@link #allOk()}
     * @throws YFinanceException the first non-{@code Ok} outcome's, in input order
     */
    public List<T> orElseThrowAll() {
        for (var outcome : outcomes) {
            if (!(outcome instanceof Outcome.Ok<T>)) {
                outcome.orElseThrow();
            }
        }
        return values();
    }

    /**
     * A batch of the same shape whose {@code Ok} values are transformed by {@code fn}; skipped and
     * failed outcomes are carried over unchanged, keeping their position, symbol, reason and error.
     *
     * @param fn applied to each {@code Ok} value; must not return {@code null}
     * @param <R> the new value type
     * @return the mapped batch, same size and order
     */
    public <R> Batch<R> map(Function<? super T, ? extends R> fn) {
        var mapped = new ArrayList<Outcome<R>>(outcomes.size());
        for (var outcome : outcomes) {
            mapped.add(outcome.map(fn));
        }
        return new Batch<>(mapped);
    }

    /**
     * The first outcome for {@code symbol}, if it was in the input. A linear scan: a batch is
     * usually iterated once, so no index is kept; use {@link #toMap()} for repeated lookups of values.
     */
    public Optional<Outcome<T>> get(Symbol symbol) {
        return outcomes.stream().filter(o -> o.symbol().equals(symbol)).findFirst();
    }

    /** One line for logs: {@code "3 symbols: 1 ok, 1 skipped, 1 failed"}. */
    public String summary() {
        int skipped = skipped().size();
        int failed = failed().size();
        return size() + " symbols: " + (size() - skipped - failed) + " ok, " + skipped + " skipped, " + failed + " failed";
    }
}
