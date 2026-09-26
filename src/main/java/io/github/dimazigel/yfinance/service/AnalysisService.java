package io.github.dimazigel.yfinance.service;

import io.github.dimazigel.yfinance.dto.quotesummary.QuoteSummaryResponse;
import io.github.dimazigel.yfinance.enums.QuoteSummaryModule;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.mapper.AnalysisMapper;
import io.github.dimazigel.yfinance.mapper.QuoteSummaryMapper;
import io.github.dimazigel.yfinance.model.AnalystPriceTarget;
import io.github.dimazigel.yfinance.model.EarningsHistoryEntry;
import io.github.dimazigel.yfinance.model.EpsRevisionsPeriod;
import io.github.dimazigel.yfinance.model.EpsTrendPeriod;
import io.github.dimazigel.yfinance.model.GrowthEstimate;
import io.github.dimazigel.yfinance.model.PeriodEstimate;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Retrieves analyst estimates and price targets. */
public final class AnalysisService {

    private static final List<QuoteSummaryModule> MODULES = List.of(
            QuoteSummaryModule.FINANCIAL_DATA,
            QuoteSummaryModule.EARNINGS_TREND,
            QuoteSummaryModule.EARNINGS_HISTORY,
            QuoteSummaryModule.RECOMMENDATION_TREND);

    private final QuoteService quoteService;

    public AnalysisService(QuoteService quoteService) {
        this.quoteService = Objects.requireNonNull(quoteService, "quoteService");
    }

    /** Analyst price targets, or {@code null} when Yahoo has no {@code financialData} for the symbol. */
    public @Nullable AnalystPriceTarget getAnalystPriceTargets(Symbol symbol) {
        return AnalysisMapper.toPriceTarget(result(symbol));
    }

    public List<PeriodEstimate> getEarningsEstimate(Symbol symbol) {
        return AnalysisMapper.toEarningsEstimate(result(symbol));
    }

    public List<PeriodEstimate> getRevenueEstimate(Symbol symbol) {
        return AnalysisMapper.toRevenueEstimate(result(symbol));
    }

    public List<EarningsHistoryEntry> getEarningsHistory(Symbol symbol) {
        return AnalysisMapper.toEarningsHistory(result(symbol));
    }

    public List<EpsTrendPeriod> getEpsTrend(Symbol symbol) {
        return AnalysisMapper.toEpsTrend(result(symbol));
    }

    public List<EpsRevisionsPeriod> getEpsRevisions(Symbol symbol) {
        return AnalysisMapper.toEpsRevisions(result(symbol));
    }

    public List<GrowthEstimate> getGrowthEstimates(Symbol symbol) {
        return AnalysisMapper.toGrowthEstimates(result(symbol));
    }

    private QuoteSummaryResponse.Result result(Symbol symbol) {
        try (var ignored = LogContext.scope("analysis", symbol)) {
            var response = quoteService.fetch(symbol, MODULES);
            return QuoteSummaryMapper.requireResult(response, symbol);
        }
    }
}
