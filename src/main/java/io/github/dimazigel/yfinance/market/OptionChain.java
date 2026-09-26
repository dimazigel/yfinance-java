package io.github.dimazigel.yfinance.market;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.List;

/**
 * The listed option contracts for one expiration of an underlying instrument.
 *
 * <p>Obtained only through {@code OptionsService}/{@code Ticker}, which return {@code
 * Optional<OptionChain>}: empty when the underlying has no listed options at all (a discovered
 * capability, not a promise of any instrument class — an index may have hundreds of contracts,
 * an equity on a foreign exchange typically none). Once a chain exists, every field below is
 * guaranteed.
 *
 * @param expirationDates all expirations available for the underlying, most recent first as Yahoo
 *                         reports them
 * @param expiration       the expiration this chain's {@link #calls()}/{@link #puts()} belong to:
 *                          the nearest one, or the one explicitly requested
 */
public record OptionChain(
        Symbol underlyingSymbol,
        List<Instant> expirationDates,
        Instant expiration,
        List<OptionContract> calls,
        List<OptionContract> puts) {

    public OptionChain {
        expirationDates = List.copyOf(expirationDates);
        calls = List.copyOf(calls);
        puts = List.copyOf(puts);
    }
}
