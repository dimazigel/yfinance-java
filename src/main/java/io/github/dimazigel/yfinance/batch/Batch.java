package io.github.dimazigel.yfinance.batch;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** N symbols in, N outcomes out, in input order. Never throws per symbol. */
public record Batch<T>(List<Outcome<T>> outcomes) {

    public Batch {
        outcomes = List.copyOf(outcomes);
    }

    public int size() {
        return outcomes.size();
    }

    /** Values of the {@link Outcome.Ok} outcomes, in order. */
    public List<T> values() {
        return outcomes.stream().flatMap(o -> o.optional().stream()).toList();
    }

    public List<Outcome.Skipped<T>> skipped() {
        var result = new ArrayList<Outcome.Skipped<T>>();
        for (var outcome : outcomes) {
            if (outcome instanceof Outcome.Skipped<T> s) {
                result.add(s);
            }
        }
        return List.copyOf(result);
    }

    public List<Outcome.Failed<T>> failed() {
        var result = new ArrayList<Outcome.Failed<T>>();
        for (var outcome : outcomes) {
            if (outcome instanceof Outcome.Failed<T> f) {
                result.add(f);
            }
        }
        return List.copyOf(result);
    }

    /** The first outcome for {@code symbol}, if it was in the input. */
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
