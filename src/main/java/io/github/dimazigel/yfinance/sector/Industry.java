package io.github.dimazigel.yfinance.sector;

import io.github.dimazigel.yfinance.enums.SectorKey;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One of Yahoo Finance's industries, as its industry page shows it.
 *
 * @param key the industry's key, e.g. {@code semiconductors}
 * @param name display name, e.g. {@code Semiconductors}
 * @param symbol the symbol of Yahoo's index for the industry, e.g. {@code ^YH31130020}
 * @param sectorKey the key of the sector it belongs to, as Yahoo spells it; see {@link #sector()}
 * @param sectorName that sector's display name
 * @param overview size of the industry
 * @param performance the industry's price performance
 * @param benchmark the index it is compared against, with that index's performance
 * @param topCompanies the largest companies, in Yahoo's order (up to 50; empty for a few industries)
 * @param topPerformingCompanies the companies with the best return since the start of the year
 * @param topGrowthCompanies the companies with the highest growth estimates
 * @param researchReports recent research reports
 * @param fetchedAt when this was fetched
 */
public record Industry(
        String key,
        String name,
        Symbol symbol,
        String sectorKey,
        String sectorName,
        Overview overview,
        Performance performance,
        Benchmark benchmark,
        List<TopCompany> topCompanies,
        List<PerformingCompany> topPerformingCompanies,
        List<GrowthCompany> topGrowthCompanies,
        List<ResearchReport> researchReports,
        Instant fetchedAt) {

    public Industry {
        topCompanies = List.copyOf(topCompanies);
        topPerformingCompanies = List.copyOf(topPerformingCompanies);
        topGrowthCompanies = List.copyOf(topGrowthCompanies);
        researchReports = List.copyOf(researchReports);
    }

    /** The sector this industry belongs to; empty only if Yahoo names a sector this library does not know. */
    public Optional<SectorKey> sector() {
        return SectorKey.ofKey(sectorKey);
    }

    /**
     * A company among the industry's best performers.
     *
     * @param symbol the company's ticker symbol
     * @param name the company's name
     * @param lastPrice last regular-session price
     * @param ytdReturn return since the start of the year, as a fraction
     * @param targetPrice analysts' mean target price
     */
    public record PerformingCompany(
            Symbol symbol,
            Optional<String> name,
            BigDecimal lastPrice,
            Optional<BigDecimal> ytdReturn,
            Optional<BigDecimal> targetPrice) {}

    /**
     * A company among the industry's fastest growers.
     *
     * @param symbol the company's ticker symbol
     * @param name the company's name
     * @param lastPrice last regular-session price
     * @param ytdReturn return since the start of the year, as a fraction
     * @param growthEstimate analysts' growth estimate, as a fraction
     */
    public record GrowthCompany(
            Symbol symbol,
            Optional<String> name,
            BigDecimal lastPrice,
            Optional<BigDecimal> ytdReturn,
            Optional<BigDecimal> growthEstimate) {}
}
