package io.github.dimazigel.yfinance.instrument;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;

/**
 * An instrument as Yahoo describes it, typed by asset class. Sealed: a {@code switch} over it is
 * exhaustive. Every class carries the universal {@link Core}; class records add what their class
 * guarantees (non-null) or sometimes has ({@code Optional}); see the design's Appendix A.
 */
public sealed interface Instrument permits Equity, Etf, MutualFund, Index, Crypto, FxPair, Future, Unclassified {

    Core core();

    AssetClass assetClass();

    /** When this snapshot was fetched (wall clock); the as-of for storage. */
    Instant fetchedAt();

    default Symbol symbol() {
        return core().symbol();
    }
}
