package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import java.time.Instant;

/** {@link Resolved} → {@link MutualFund}. Callers must have checked {@code missingRequired()} first. */
public final class MutualFundBuilder {

    private MutualFundBuilder() {}

    public static MutualFund build(Resolved r, Instant fetchedAt) {
        return new MutualFund(
                CoreBuilder.build(r),
                r.decimal("netAssets"),
                r.decimal("expenseRatio"),
                r.decimal("yield"),
                r.decimal("dividendRate"),
                r.decimal("ytdReturn"),
                r.decimal("threeMonthReturn"),
                TierBuilders.equityLikeStats(r),
                r.optDecimal("trailingPE"),
                TierBuilders.trailingDividend(r),
                fetchedAt);
    }
}
