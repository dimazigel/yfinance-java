package io.github.dimazigel.yfinance.internal.mapper;

import io.github.dimazigel.yfinance.enums.SectorKey;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.internal.dto.domain.DomainResponse;
import io.github.dimazigel.yfinance.sector.Benchmark;
import io.github.dimazigel.yfinance.sector.Industry;
import io.github.dimazigel.yfinance.sector.Overview;
import io.github.dimazigel.yfinance.sector.Performance;
import io.github.dimazigel.yfinance.sector.ResearchReport;
import io.github.dimazigel.yfinance.sector.Sector;
import io.github.dimazigel.yfinance.sector.TopCompany;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps the sector and industry responses to {@link Sector} and {@link Industry}.
 *
 * <p>The head of a page — name, symbol, overview, performance, benchmark — was complete for every
 * one of the 11 sectors and 145 industries surveyed live on 2026-10-01, so a page without it is a
 * {@link YFDataException}. List rows are mapped one by one: a row missing a field the survey found
 * on every row of its list is dropped (DEBUG), the rest of the page stands.
 */
public final class DomainMapper {

    private static final Logger LOG = LoggerFactory.getLogger(DomainMapper.class);

    private DomainMapper() {}

    public static Sector toSector(DomainResponse response, SectorKey key, Instant fetchedAt) {
        DomainResponse.Data data = response.data();
        if (data == null) {
            throw incomplete("sector", key.wireValue(), List.of("data"));
        }
        String name = text(data.name());
        Symbol symbol = symbol(data.symbol());
        Overview overview = overview(data.overview());
        Integer industriesCount = data.overview() == null ? null : data.overview().industriesCount();
        Performance performance = performance(data.performance());
        Benchmark benchmark = benchmark(data.performanceOverviewBenchmark());
        if (name == null || symbol == null || overview == null || industriesCount == null || performance == null
                || benchmark == null) {
            throw incomplete("sector", key.wireValue(), missing(
                    "name", name, "symbol", symbol, "overview", overview, "overview.industriesCount", industriesCount,
                    "performance", performance, "benchmark", benchmark));
        }
        return new Sector(
                key,
                name,
                symbol,
                overview,
                industriesCount,
                performance,
                benchmark,
                rows(data.topCompanies(), "topCompanies", DomainMapper::topCompany),
                rows(data.topETFs(), "topETFs", DomainMapper::fund),
                rows(data.topMutualFunds(), "topMutualFunds", DomainMapper::fund),
                rows(industries(data.industries()), "industries", DomainMapper::industrySummary),
                rows(data.researchReports(), "researchReports", DomainMapper::report),
                fetchedAt);
    }

    public static Industry toIndustry(DomainResponse response, String key, Instant fetchedAt) {
        DomainResponse.Data data = response.data();
        if (data == null) {
            throw incomplete("industry", key, List.of("data"));
        }
        String name = text(data.name());
        Symbol symbol = symbol(data.symbol());
        String sectorKey = text(data.sectorKey());
        String sectorName = text(data.sectorName());
        Overview overview = overview(data.overview());
        Performance performance = performance(data.performance());
        Benchmark benchmark = benchmark(data.performanceOverviewBenchmark());
        if (name == null || symbol == null || sectorKey == null || sectorName == null || overview == null
                || performance == null || benchmark == null) {
            throw incomplete("industry", key, missing(
                    "name", name, "symbol", symbol, "sectorKey", sectorKey, "sectorName", sectorName,
                    "overview", overview, "performance", performance, "benchmark", benchmark));
        }
        return new Industry(
                key,
                name,
                symbol,
                sectorKey,
                sectorName,
                overview,
                performance,
                benchmark,
                rows(data.topCompanies(), "topCompanies", DomainMapper::topCompany),
                rows(data.topPerformingCompanies(), "topPerformingCompanies", DomainMapper::performingCompany),
                rows(data.topGrowthCompanies(), "topGrowthCompanies", DomainMapper::growthCompany),
                rows(data.researchReports(), "researchReports", DomainMapper::report),
                fetchedAt);
    }

    // ---- head ----

    private static @Nullable Overview overview(DomainResponse.@Nullable Overview o) {
        if (o == null) {
            return null;
        }
        Integer companiesCount = o.companiesCount();
        BigDecimal marketCap = o.marketCap();
        BigDecimal marketWeight = o.marketWeight();
        Long employeeCount = o.employeeCount();
        String description = text(o.description());
        String messageBoardId = text(o.messageBoardId());
        if (companiesCount == null || marketCap == null || marketWeight == null || employeeCount == null
                || description == null || messageBoardId == null) {
            return null;
        }
        return new Overview(companiesCount, marketCap, marketWeight, employeeCount, description, messageBoardId);
    }

    private static @Nullable Performance performance(DomainResponse.@Nullable Performance p) {
        if (p == null) {
            return null;
        }
        BigDecimal day = p.regMarketChangePercent();
        BigDecimal ytd = p.ytdChangePercent();
        BigDecimal oneYear = p.oneYearChangePercent();
        BigDecimal threeYear = p.threeYearChangePercent();
        BigDecimal fiveYear = p.fiveYearChangePercent();
        if (day == null || ytd == null || oneYear == null || threeYear == null || fiveYear == null) {
            return null;
        }
        return new Performance(day, ytd, oneYear, threeYear, fiveYear);
    }

    private static @Nullable Benchmark benchmark(DomainResponse.@Nullable Performance p) {
        Performance performance = performance(p);
        String name = p == null ? null : text(p.name());
        return performance == null || name == null ? null : new Benchmark(name, performance);
    }

    // ---- rows ----

    /** A sector's list opens with an "All Industries" total that has neither key nor symbol; it is not an industry. */
    private static @Nullable List<DomainResponse.@Nullable Row> industries(@Nullable List<DomainResponse.@Nullable Row> rows) {
        if (rows == null) {
            return null;
        }
        return rows.stream().filter(r -> r == null || r.key() != null || r.symbol() != null).toList();
    }

    private static @Nullable TopCompany topCompany(DomainResponse.Row r) {
        Symbol symbol = symbol(r.symbol());
        BigDecimal lastPrice = r.lastPrice();
        BigDecimal marketCap = r.marketCap();
        BigDecimal marketWeight = r.marketWeight();
        BigDecimal dayChange = r.regMarketChangePercent();
        if (symbol == null || lastPrice == null || marketCap == null || marketWeight == null || dayChange == null) {
            return null;
        }
        return new TopCompany(symbol, optText(r.name()), lastPrice, marketCap, marketWeight, dayChange,
                Optional.ofNullable(r.ytdReturn()), optText(r.rating()), Optional.ofNullable(r.targetPrice()));
    }

    private static Sector.@Nullable Fund fund(DomainResponse.Row r) {
        Symbol symbol = symbol(r.symbol());
        BigDecimal lastPrice = r.lastPrice();
        BigDecimal netAssets = r.netAssets();
        BigDecimal expenseRatio = r.expenseRatio();
        if (symbol == null || lastPrice == null || netAssets == null || expenseRatio == null) {
            return null;
        }
        return new Sector.Fund(symbol, optText(r.name()), lastPrice, netAssets, expenseRatio, Optional.ofNullable(r.ytdReturn()));
    }

    private static Sector.@Nullable IndustrySummary industrySummary(DomainResponse.Row r) {
        String key = text(r.key());
        String name = text(r.name());
        Symbol symbol = symbol(r.symbol());
        BigDecimal marketWeight = r.marketWeight();
        BigDecimal dayChange = r.regMarketChangePercent();
        BigDecimal ytdReturn = r.ytdReturn();
        if (key == null || name == null || symbol == null || marketWeight == null || dayChange == null || ytdReturn == null) {
            return null;
        }
        return new Sector.IndustrySummary(key, name, symbol, marketWeight, dayChange, ytdReturn);
    }

    private static Industry.@Nullable PerformingCompany performingCompany(DomainResponse.Row r) {
        Symbol symbol = symbol(r.symbol());
        BigDecimal lastPrice = r.lastPrice();
        if (symbol == null || lastPrice == null) {
            return null;
        }
        return new Industry.PerformingCompany(symbol, optText(r.name()), lastPrice,
                Optional.ofNullable(r.ytdReturn()), Optional.ofNullable(r.targetPrice()));
    }

    private static Industry.@Nullable GrowthCompany growthCompany(DomainResponse.Row r) {
        Symbol symbol = symbol(r.symbol());
        BigDecimal lastPrice = r.lastPrice();
        if (symbol == null || lastPrice == null) {
            return null;
        }
        return new Industry.GrowthCompany(symbol, optText(r.name()), lastPrice,
                Optional.ofNullable(r.ytdReturn()), Optional.ofNullable(r.growthEstimate()));
    }

    private static @Nullable ResearchReport report(DomainResponse.Report r) {
        String id = text(r.id());
        String title = text(r.headHtml());
        String provider = text(r.provider());
        String type = text(r.reportType());
        Instant published = MapperSupport.instant(r.reportDate());
        String summary = text(r.reportTitle());
        if (id == null || title == null || provider == null || type == null || published == null || summary == null) {
            return null;
        }
        return new ResearchReport(id, title, provider, type, published, summary,
                optText(r.investmentRating()), Optional.ofNullable(r.targetPrice()), optText(r.targetPriceStatus()));
    }

    /** Maps a list row by row in Yahoo's order; a missing list is empty, an unmappable row is dropped and counted. */
    private static <R, T> List<T> rows(@Nullable List<@Nullable R> rows, String list, Function<R, @Nullable T> map) {
        if (rows == null) {
            return List.of();
        }
        var mapped = new ArrayList<T>(rows.size());
        for (R row : rows) {
            T value = row == null ? null : map.apply(row);
            if (value != null) {
                mapped.add(value);
            }
        }
        int dropped = rows.size() - mapped.size();
        if (dropped > 0) {
            LOG.atDebug().addKeyValue("dropped", dropped).addKeyValue("total", rows.size())
                    .log("Dropped {} of {} {} rows without a complete required field", dropped, rows.size(), list);
        }
        return mapped;
    }

    // ---- leaves ----

    private static @Nullable Symbol symbol(@Nullable String value) {
        String text = text(value);
        return text == null ? null : Symbol.of(text);
    }

    private static @Nullable String text(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Optional<String> optText(@Nullable String value) {
        return Optional.ofNullable(text(value));
    }

    /** The names, from {@code name, value, name, value, …}, whose value is null. */
    private static List<String> missing(@Nullable Object... namesAndValues) {
        var names = new ArrayList<String>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (namesAndValues[i + 1] == null) {
                names.add(String.valueOf(namesAndValues[i]));
            }
        }
        return names;
    }

    private static YFDataException incomplete(String kind, String key, List<String> missing) {
        return new YFDataException("Incomplete " + kind + " data for " + key + ": missing " + missing);
    }
}
