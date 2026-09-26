package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.Etf;
import java.time.Instant;

/** {@link Resolved} → {@link Etf}. Callers must have checked {@code missingRequired()} first. */
public final class EtfBuilder {

    private EtfBuilder() {}

    public static Etf build(Resolved r, Instant fetchedAt) {
        return new Etf(
                CoreBuilder.build(r),
                TierBuilders.session(r),
                TierBuilders.book(r),
                r.decimal("ytdReturn"),
                r.decimal("threeMonthReturn"),
                r.optDecimal("netAssets"),
                r.optDecimal("expenseRatio"),
                r.optDecimal("yield"),
                r.optDecimal("navPrice"),
                r.optDecimal("beta3Year"),
                r.optDecimal("trailingThreeMonthNavReturns"),
                r.optDecimal("trailingPE"),
                TierBuilders.equityLikeStats(r),
                TierBuilders.trailingDividend(r),
                TierBuilders.postMarket(r),
                fetchedAt);
    }
}
