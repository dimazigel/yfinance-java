package io.github.dimazigel.yfinance.testsupport;

import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.build.EquityBuilder;
import io.github.dimazigel.yfinance.assembly.specs.EquitySpecs;
import io.github.dimazigel.yfinance.instrument.Core;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;

/**
 * Real, fixture-backed {@link Equity} instances for tests that need one — a compile-time proof
 * token, or a concrete instrument to exercise a service against — without hand-rolling
 * {@link Core}'s and {@link Equity}'s positional constructors with magic values in every test class.
 */
public final class Instruments {

    private Instruments() {}

    /**
     * The real AAPL equity assembled from the captured fixtures (see {@link InstrumentFixtures}),
     * with its symbol swapped to {@code symbol} when that's not {@code "AAPL"} (see
     * {@link #withSymbol}).
     */
    public static Equity equity(String symbol) {
        Equity aapl = EquityBuilder.build(
                Resolver.resolve(InstrumentFixtures.payload("AAPL", false), EquitySpecs.SNAPSHOT), Instant.EPOCH);
        return "AAPL".equals(symbol) ? aapl : withSymbol(aapl, symbol);
    }

    /** {@code e} with its {@link Core#symbol()} replaced by {@code symbol}; every other field unchanged. */
    public static Equity withSymbol(Equity e, String symbol) {
        Core core = e.core();
        Core replaced = new Core(
                Symbol.of(symbol),
                core.shortName(),
                core.longName(),
                core.currency(),
                core.exchange(),
                core.fullExchangeName(),
                core.exchangeTimezone(),
                core.marketState(),
                core.price(),
                core.change(),
                core.changePercent(),
                core.previousClose(),
                core.priceTime(),
                core.fiftyTwoWeekLow(),
                core.fiftyTwoWeekHigh(),
                core.fiftyDayAverage(),
                core.twoHundredDayAverage(),
                core.averageVolume10Day(),
                core.averageVolume3Month(),
                core.firstTradeDate(),
                core.priceHint(),
                core.hasPrePostMarketData());
        return new Equity(
                replaced,
                e.session(),
                e.book(),
                e.valuation(),
                e.nextEarnings(),
                e.bookValue(),
                e.priceToBook(),
                e.trailingEps(),
                e.forwardEps(),
                e.forwardPE(),
                e.trailingPE(),
                e.trailingDividend(),
                e.currentDividend(),
                e.currentYearEps(),
                e.averageAnalystRating(),
                e.postMarket(),
                e.fetchedAt());
    }
}
