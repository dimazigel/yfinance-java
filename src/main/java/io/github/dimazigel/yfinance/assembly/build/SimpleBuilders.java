package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.FxPair;
import io.github.dimazigel.yfinance.instrument.Index;
import java.time.Instant;

/** {@link Resolved} → {@link Index} or {@link FxPair}. Callers must have checked {@code missingRequired()} first. */
public final class SimpleBuilders {

    private SimpleBuilders() {}

    public static Index index(Resolved r, Instant fetchedAt) {
        return new Index(CoreBuilder.build(r), TierBuilders.session(r), TierBuilders.book(r), fetchedAt);
    }

    public static FxPair fxPair(Resolved r, Instant fetchedAt) {
        return new FxPair(CoreBuilder.build(r), TierBuilders.session(r), TierBuilders.book(r), fetchedAt);
    }
}
