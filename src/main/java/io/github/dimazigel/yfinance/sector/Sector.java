package io.github.dimazigel.yfinance.sector;

import io.github.dimazigel.yfinance.enums.SectorKey;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One of Yahoo Finance's sectors, as its sector page shows it.
 *
 * @param key which sector
 * @param name display name, e.g. {@code Technology}
 * @param symbol the symbol of Yahoo's index for the sector, e.g. {@code ^YH311}
 * @param overview size of the sector
 * @param industriesCount how many industries it has
 * @param performance the sector's price performance
 * @param benchmark the index it is compared against, with that index's performance
 * @param topCompanies the largest companies, in Yahoo's order (up to 50)
 * @param topEtfs the largest ETFs focused on the sector
 * @param topMutualFunds the largest mutual funds focused on the sector
 * @param industries the sector's industries, largest first; each {@code key} opens the industry
 * @param researchReports recent research reports
 * @param fetchedAt when this was fetched
 */
public record Sector(
        SectorKey key,
        String name,
        Symbol symbol,
        Overview overview,
        int industriesCount,
        Performance performance,
        Benchmark benchmark,
        List<TopCompany> topCompanies,
        List<Fund> topEtfs,
        List<Fund> topMutualFunds,
        List<IndustrySummary> industries,
        List<ResearchReport> researchReports,
        Instant fetchedAt) {

    public Sector {
        topCompanies = List.copyOf(topCompanies);
        topEtfs = List.copyOf(topEtfs);
        topMutualFunds = List.copyOf(topMutualFunds);
        industries = List.copyOf(industries);
        researchReports = List.copyOf(researchReports);
    }

    /**
     * An ETF or a mutual fund focused on the sector.
     *
     * @param symbol the fund's ticker symbol
     * @param name the fund's name
     * @param lastPrice last price (the net asset value for a mutual fund)
     * @param netAssets assets under management
     * @param expenseRatio annual expense ratio, as a fraction
     * @param ytdReturn return since the start of the year, as a fraction
     */
    public record Fund(
            Symbol symbol,
            Optional<String> name,
            BigDecimal lastPrice,
            BigDecimal netAssets,
            BigDecimal expenseRatio,
            Optional<BigDecimal> ytdReturn) {}

    /**
     * One industry of the sector.
     *
     * @param key the industry's key, the argument of {@code YFinance.industry(key)}
     * @param name display name, e.g. {@code Semiconductors}
     * @param symbol the symbol of Yahoo's index for the industry
     * @param marketWeight the industry's share of the sector, as a fraction
     * @param dayChangePercent change in the latest regular session, as a fraction
     * @param ytdReturn return since the start of the year, as a fraction
     */
    public record IndustrySummary(
            String key,
            String name,
            Symbol symbol,
            BigDecimal marketWeight,
            BigDecimal dayChangePercent,
            BigDecimal ytdReturn) {}
}
