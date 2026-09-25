package io.github.dimazigel.yfinance.mapper;

import io.github.dimazigel.yfinance.dto.quotesummary.QuoteSummaryResponse.EarningsHistory;
import io.github.dimazigel.yfinance.dto.quotesummary.QuoteSummaryResponse.EarningsTrend.Estimate;
import io.github.dimazigel.yfinance.dto.quotesummary.QuoteSummaryResponse.EarningsTrend.TrendRow;
import io.github.dimazigel.yfinance.dto.quotesummary.QuoteSummaryResponse.FinancialData;
import io.github.dimazigel.yfinance.dto.quotesummary.QuoteSummaryResponse.Result;
import io.github.dimazigel.yfinance.model.AnalystPriceTarget;
import io.github.dimazigel.yfinance.model.EarningsHistoryEntry;
import io.github.dimazigel.yfinance.model.EpsRevisionsPeriod;
import io.github.dimazigel.yfinance.model.EpsTrendPeriod;
import io.github.dimazigel.yfinance.model.GrowthEstimate;
import io.github.dimazigel.yfinance.model.PeriodEstimate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Maps analysis-related quoteSummary modules into analyst models. */
public final class AnalysisMapper {

    private AnalysisMapper() {}

    public static @Nullable AnalystPriceTarget toPriceTarget(Result r) {
        FinancialData fd = r.financialData();
        if (fd == null) {
            return null;
        }
        return new AnalystPriceTarget(
                fd.currentPrice(), fd.targetLowPrice(), fd.targetHighPrice(),
                fd.targetMeanPrice(), fd.targetMedianPrice(), fd.numberOfAnalystOpinions());
    }

    public static List<PeriodEstimate> toEarningsEstimate(Result r) {
        return estimates(r, TrendRow::earningsEstimate);
    }

    public static List<PeriodEstimate> toRevenueEstimate(Result r) {
        return estimates(r, TrendRow::revenueEstimate);
    }

    public static List<EarningsHistoryEntry> toEarningsHistory(Result r) {
        EarningsHistory history = r.earningsHistory();
        if (history == null || history.history() == null) {
            return List.of();
        }
        return history.history().stream()
                .map(h -> new EarningsHistoryEntry(
                        h.period(),
                        MapperSupport.epochSecond(h.quarter()),
                        h.epsActual(), h.epsEstimate(), h.epsDifference(), h.surprisePercent()))
                .toList();
    }

    public static List<EpsTrendPeriod> toEpsTrend(Result r) {
        return mapRows(r, row -> {
            var t = row.epsTrend();
            return t == null ? null : new EpsTrendPeriod(
                    row.period(), endDate(row),
                    t.current(), t.sevenDaysAgo(), t.thirtyDaysAgo(), t.sixtyDaysAgo(), t.ninetyDaysAgo());
        });
    }

    public static List<EpsRevisionsPeriod> toEpsRevisions(Result r) {
        return mapRows(r, row -> {
            var rev = row.epsRevisions();
            return rev == null ? null : new EpsRevisionsPeriod(
                    row.period(), endDate(row),
                    rev.upLast7Days(), rev.upLast30Days(), rev.downLast30Days(), rev.downLast90Days());
        });
    }

    public static List<GrowthEstimate> toGrowthEstimates(Result r) {
        return mapRows(r, row -> {
            var growth = row.growth();
            return growth == null ? null : new GrowthEstimate(row.period(), endDate(row), growth);
        });
    }

    /** Maps each trend row, skipping rows for which {@code fn} yields {@code null}. */
    private static <R> List<R> mapRows(Result r, Function<TrendRow, @Nullable R> fn) {
        var out = new ArrayList<R>();
        for (TrendRow row : trendRows(r)) {
            R mapped = fn.apply(row);
            if (mapped != null) {
                out.add(mapped);
            }
        }
        return List.copyOf(out);
    }

    private static List<TrendRow> trendRows(Result r) {
        if (r.earningsTrend() == null || r.earningsTrend().trend() == null) {
            return List.of();
        }
        return r.earningsTrend().trend();
    }

    private static @Nullable LocalDate endDate(TrendRow row) {
        return MapperSupport.localDate(row.endDate());
    }

    private static List<PeriodEstimate> estimates(Result r, Function<TrendRow, @Nullable Estimate> pick) {
        return trendRows(r).stream()
                .map(row -> {
                    Estimate e = pick.apply(row);
                    return new PeriodEstimate(
                            row.period(),
                            endDate(row),
                            e != null ? e.avg() : null,
                            e != null ? e.low() : null,
                            e != null ? e.high() : null,
                            e != null ? e.numberOfAnalysts() : null,
                            e != null ? e.yearAgoEps() : null);
                })
                .toList();
    }
}
