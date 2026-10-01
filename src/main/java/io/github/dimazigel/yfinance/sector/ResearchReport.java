package io.github.dimazigel.yfinance.sector;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * A research report Yahoo lists on a sector or industry page. The rating and target-price
 * components are present on analyst reports about one company and absent on market digests.
 *
 * @param id Yahoo's identifier of the report
 * @param title the headline, e.g. {@code Analyst Report: Jabil Inc}
 * @param provider who wrote it, e.g. {@code Argus Research}
 * @param type the kind of report, e.g. {@code Analyst Report} or {@code Market Summary}
 * @param published when it was published
 * @param summary the report's opening text
 * @param investmentRating the report's rating, e.g. {@code Bullish}
 * @param targetPrice the report's target price
 * @param targetPriceStatus what happened to the target, e.g. {@code Maintained}
 */
public record ResearchReport(
        String id,
        String title,
        String provider,
        String type,
        Instant published,
        String summary,
        Optional<String> investmentRating,
        Optional<BigDecimal> targetPrice,
        Optional<String> targetPriceStatus) {}
