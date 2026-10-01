package io.github.dimazigel.yfinance.internal.dto.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Raw deserialization of the {@code /v1/finance/sectors/{key}} and {@code /v1/finance/industries/{key}}
 * responses, which share one shape: each endpoint fills the members it has. Numbers arrive as
 * {@code {raw, fmt}} objects whatever {@code formatted} says; {@code RawAwareNumberModule} unwraps them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DomainResponse(@Nullable Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @Nullable String key,
            @Nullable String name,
            @Nullable String symbol,
            @Nullable String sectorKey,
            @Nullable String sectorName,
            @Nullable Overview overview,
            @Nullable Performance performance,
            @Nullable Performance performanceOverviewBenchmark,
            @Nullable List<@Nullable Row> topCompanies,
            @Nullable List<@Nullable Row> topETFs,
            @Nullable List<@Nullable Row> topMutualFunds,
            @Nullable List<@Nullable Row> industries,
            @Nullable List<@Nullable Row> topPerformingCompanies,
            @Nullable List<@Nullable Row> topGrowthCompanies,
            @Nullable List<@Nullable Report> researchReports) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Overview(
            @Nullable Integer companiesCount,
            @Nullable Integer industriesCount,
            @Nullable BigDecimal marketCap,
            @Nullable BigDecimal marketWeight,
            @Nullable Long employeeCount,
            @Nullable String description,
            @Nullable String messageBoardId) {}

    /** {@code name} is set on the benchmark only. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Performance(
            @Nullable String name,
            @Nullable BigDecimal regMarketChangePercent,
            @Nullable BigDecimal ytdChangePercent,
            @Nullable BigDecimal oneYearChangePercent,
            @Nullable BigDecimal threeYearChangePercent,
            @Nullable BigDecimal fiveYearChangePercent) {}

    /** A row of any of the lists; each list fills its own subset of the members. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            @Nullable String symbol,
            @Nullable String key,
            @Nullable String name,
            @Nullable String rating,
            @Nullable BigDecimal lastPrice,
            @Nullable BigDecimal marketCap,
            @Nullable BigDecimal marketWeight,
            @Nullable BigDecimal regMarketChangePercent,
            @Nullable BigDecimal ytdReturn,
            @Nullable BigDecimal targetPrice,
            @Nullable BigDecimal netAssets,
            @Nullable BigDecimal expenseRatio,
            @Nullable BigDecimal growthEstimate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Report(
            @Nullable String id,
            @Nullable String headHtml,
            @Nullable String provider,
            @Nullable String reportDate,
            @Nullable String reportTitle,
            @Nullable String reportType,
            @Nullable String investmentRating,
            @Nullable BigDecimal targetPrice,
            @Nullable String targetPriceStatus) {}
}
