package io.ziggy.yfinance.model;

import io.ziggy.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An option chain for one expiration.
 *
 * @param expirationDates all expirations available for the underlying
 * @param expiration      the expiration this chain's contracts belong to
 */
public record OptionChain(
        Symbol underlyingSymbol,
        List<Instant> expirationDates,
        @Nullable Instant expiration,
        List<OptionContract> calls,
        List<OptionContract> puts) {

    public OptionChain {
        expirationDates = expirationDates == null ? List.of() : List.copyOf(expirationDates);
        calls = calls == null ? List.of() : List.copyOf(calls);
        puts = puts == null ? List.of() : List.copyOf(puts);
    }
}
