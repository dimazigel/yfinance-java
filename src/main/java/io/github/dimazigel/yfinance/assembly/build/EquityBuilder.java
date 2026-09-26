package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import java.time.Instant;
import java.util.Optional;

/** {@link Resolved} → {@link Equity}. Callers must have checked {@code missingRequired()} first. */
public final class EquityBuilder {

    private EquityBuilder() {}

    public static Equity build(Resolved r, Instant fetchedAt) {
        return new Equity(
                CoreBuilder.build(r),
                TierBuilders.session(r),
                TierBuilders.book(r),
                new Equity.Valuation(r.decimal("marketCap"), r.longValue("sharesOutstanding"),
                        r.longValue("impliedSharesOutstanding"), QuoteCurrency.of(r.string("financialCurrency"))),
                new Equity.NextEarnings(r.instant("nextEarnings.expected"), r.instant("nextEarnings.windowStart"),
                        r.instant("nextEarnings.windowEnd"), r.bool("nextEarnings.isEstimate")),
                r.optDecimal("bookValue"),
                r.optDecimal("priceToBook"),
                r.optDecimal("trailingEps"),
                r.optDecimal("forwardEps"),
                r.optDecimal("forwardPE"),
                r.optDecimal("trailingPE"),
                TierBuilders.trailingDividend(r),
                r.clusterPresent("currentDividend")
                        ? Optional.of(new Equity.CurrentDividend(r.decimal("currentDividend.rate"), r.decimal("currentDividend.yield")))
                        : Optional.empty(),
                r.clusterPresent("currentYearEps")
                        ? Optional.of(new Equity.CurrentYearEps(r.decimal("currentYearEps.eps"), r.decimal("currentYearEps.priceToEps")))
                        : Optional.empty(),
                r.optString("averageAnalystRating"),
                TierBuilders.postMarket(r),
                fetchedAt);
    }
}
