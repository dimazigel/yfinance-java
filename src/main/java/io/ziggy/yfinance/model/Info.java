package io.ziggy.yfinance.model;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Aggregated company information assembled from a single quoteSummary call.
 *
 * <p>When quoteSummary has no data for the instrument (typical for indices, ETFs, crypto, FX and
 * futures) the library falls back to the quote endpoint: {@code quote} is still populated, but
 * {@code profile} is {@code null} and the lists are empty.
 */
public record Info(
        @Nullable CompanyProfile profile,
        Quote quote,
        List<RecommendationPeriod> recommendationTrend,
        List<UpgradeDowngrade> upgradesDowngrades,
        List<Instant> earningsDates,
        List<SecFiling> secFilings) {

    public Info {
        recommendationTrend = recommendationTrend == null ? List.of() : List.copyOf(recommendationTrend);
        upgradesDowngrades = upgradesDowngrades == null ? List.of() : List.copyOf(upgradesDowngrades);
        earningsDates = earningsDates == null ? List.of() : List.copyOf(earningsDates);
        secFilings = secFilings == null ? List.of() : List.copyOf(secFilings);
    }
}
