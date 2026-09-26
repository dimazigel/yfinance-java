package io.github.dimazigel.yfinance.logging;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * The MDC keys this library sets, so every log line emitted while serving a call can be traced
 * back to what was being done, for which symbol, against which Yahoo endpoint:
 *
 * <ul>
 *   <li>{@value #OP} — the operation: {@code history}, {@code info}, {@code quote}, {@code quotes},
 *       {@code financials}, {@code options}, {@code holders}, {@code analysis}, {@code search},
 *       {@code lookup}
 *   <li>{@value #SYMBOL} — the ticker symbol (comma-joined for batch quotes)
 *   <li>{@value #ENDPOINT} — the request path, e.g. {@code /v8/finance/chart/AAPL}, present while an
 *       HTTP call is in flight
 * </ul>
 *
 * <p>Add them to a log pattern with e.g. Logback's {@code %X{yf.op} %X{yf.symbol} %X{yf.endpoint}}.
 * Scopes nest: an inner scope's values apply until it closes, then the outer values are restored.
 */
public final class LogContext {

    public static final String OP = "yf.op";
    public static final String SYMBOL = "yf.symbol";
    public static final String ENDPOINT = "yf.endpoint";

    private LogContext() {}

    /** Marks the current thread as performing {@code op} until the returned scope is closed. */
    public static Scope scope(String op) {
        return new Scope(Map.of(OP, op));
    }

    /** Marks the current thread as performing {@code op} for {@code symbol} until the scope is closed. */
    public static Scope scope(String op, Symbol symbol) {
        return new Scope(Map.of(OP, op, SYMBOL, symbol.value()));
    }

    /** Batch variant: {@link #SYMBOL} holds the symbols comma-joined. */
    public static Scope scope(String op, List<Symbol> symbols) {
        return new Scope(Map.of(OP, op, SYMBOL, symbols.stream().map(Symbol::value).collect(Collectors.joining(","))));
    }

    /** Marks the current thread as calling {@code path} (e.g. {@code /v8/finance/chart/AAPL}). */
    public static Scope endpoint(String path) {
        return new Scope(Map.of(ENDPOINT, path));
    }

    /** A pushed MDC context; closing restores whatever the keys held before. */
    public static final class Scope implements AutoCloseable {

        private final Map<String, @Nullable String> previous = new HashMap<>();

        private Scope(Map<String, String> values) {
            values.forEach((key, value) -> {
                previous.put(key, MDC.get(key));
                MDC.put(key, value);
            });
        }

        @Override
        public void close() {
            previous.forEach((key, value) -> {
                if (value == null) {
                    MDC.remove(key);
                } else {
                    MDC.put(key, value);
                }
            });
        }
    }
}
