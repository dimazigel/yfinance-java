package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.Future;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;

/** {@link Resolved} → {@link Future}. Callers must have checked {@code missingRequired()} first. */
public final class FutureBuilder {

    private FutureBuilder() {}

    public static Future build(Resolved r, Instant fetchedAt) {
        return new Future(
                CoreBuilder.build(r),
                TierBuilders.session(r),
                TierBuilders.book(r),
                new Future.Contract(
                        r.bool("contract.isSpecificContract"),
                        r.date("contract.expireDate"),
                        r.longValue("contract.openInterest"),
                        Symbol.of(r.string("contract.underlyingSymbol")),
                        r.string("contract.underlyingExchangeSymbol"),
                        Symbol.of(r.string("contract.headSymbol"))),
                fetchedAt);
    }
}
