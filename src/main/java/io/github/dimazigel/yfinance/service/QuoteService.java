package io.github.dimazigel.yfinance.service;

import io.github.dimazigel.yfinance.api.QuoteApi;
import io.github.dimazigel.yfinance.api.QuoteSummaryApi;
import io.github.dimazigel.yfinance.dto.quotesummary.QuoteSummaryResponse;
import io.github.dimazigel.yfinance.enums.QuoteSummaryModule;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.mapper.QuoteMapper;
import io.github.dimazigel.yfinance.mapper.QuoteSummaryMapper;
import io.github.dimazigel.yfinance.model.Info;
import io.github.dimazigel.yfinance.model.Quote;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves consolidated company info via the quoteSummary endpoint, and lightweight quotes via
 * the batch quote endpoint.
 *
 * <p>quoteSummary has no data for many non-equities (indices, ETFs, crypto, FX, futures) and is
 * inconsistent about it: the same request can answer HTTP 404 "No fundamentals data found" one
 * moment and succeed the next. When it fails, {@link #getInfo} falls back to the quote endpoint
 * (as Python yfinance does) and returns an {@link Info} whose {@code quote} is populated but whose
 * profile is {@code null} and whose trend lists are empty.
 */
public final class QuoteService {

    private static final Logger LOG = LoggerFactory.getLogger(QuoteService.class);

    /** Modules fetched to assemble {@link Info}. */
    public static final List<QuoteSummaryModule> INFO_MODULES = List.of(
            QuoteSummaryModule.ASSET_PROFILE,
            QuoteSummaryModule.QUOTE_TYPE,
            QuoteSummaryModule.PRICE,
            QuoteSummaryModule.SUMMARY_DETAIL,
            QuoteSummaryModule.FINANCIAL_DATA,
            QuoteSummaryModule.DEFAULT_KEY_STATISTICS,
            QuoteSummaryModule.CALENDAR_EVENTS,
            QuoteSummaryModule.SEC_FILINGS,
            QuoteSummaryModule.RECOMMENDATION_TREND,
            QuoteSummaryModule.UPGRADE_DOWNGRADE_HISTORY);

    private static final String CORS_DOMAIN = "finance.yahoo.com";

    private final QuoteSummaryApi api;
    private final QuoteApi quoteApi;

    public QuoteService(QuoteSummaryApi api, QuoteApi quoteApi) {
        this.api = Objects.requireNonNull(api, "api");
        this.quoteApi = Objects.requireNonNull(quoteApi, "quoteApi");
    }

    /**
     * Full info from quoteSummary, or quote-only info from the quote endpoint when quoteSummary
     * has no data for the symbol. Rate-limit and auth failures are never masked by the fallback.
     *
     * @throws YFDataException when neither endpoint knows the symbol (the quoteSummary error is
     *     what is thrown, with any fallback failure attached as a suppressed exception)
     */
    public Info getInfo(Symbol symbol) {
        // The scope must outlive the catch block below (a try-with-resources closes before catch runs).
        try (var ignored = LogContext.scope("info", symbol)) {
            return fetchInfo(symbol);
        }
    }

    private Info fetchInfo(Symbol symbol) {
        try {
            return QuoteSummaryMapper.toInfo(fetch(symbol, INFO_MODULES), symbol);
        } catch (YFDataException summaryFailure) {
            Quote quote;
            try {
                quote = getQuotes(List.of(symbol)).get(symbol);
            } catch (YFDataException fallbackFailure) {
                summaryFailure.addSuppressed(fallbackFailure);
                throw summaryFailure;
            }
            if (quote == null) {
                throw summaryFailure;
            }
            LOG.atDebug()
                    .addKeyValue("reason", summaryFailure.getMessage())
                    .log("quoteSummary has no data for {}; built Info from /v7/finance/quote", symbol);
            return new Info(null, quote, List.of(), List.of(), List.of(), List.of());
        }
    }

    /**
     * A lightweight quote for one symbol from the quote endpoint: one request, any asset class.
     *
     * @throws YFDataException when Yahoo does not know the symbol
     */
    public Quote getQuote(Symbol symbol) {
        try (var ignored = LogContext.scope("quote", symbol)) {
            Quote quote = getQuotes(List.of(symbol)).get(symbol);
            if (quote == null) {
                throw new YFDataException("No quote returned for " + symbol);
            }
            return quote;
        }
    }

    /**
     * Quotes for many symbols in a single request, keyed in the order given. Symbols Yahoo does not
     * know are absent from the result rather than failing the batch.
     */
    public Map<Symbol, Quote> getQuotes(List<Symbol> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        try (var ignored = LogContext.scope("quotes", symbols)) {
            String joined = symbols.stream().map(Symbol::value).collect(Collectors.joining(","));
            return QuoteMapper.toQuotes(quoteApi.quote(joined, false), symbols);
        }
    }

    /** Fetches an arbitrary set of modules; used by holders/analysis services. */
    public QuoteSummaryResponse fetch(Symbol symbol, List<QuoteSummaryModule> modules) {
        return api.quoteSummary(
                symbol.value(), QuoteSummaryModule.toQueryParam(modules), false, CORS_DOMAIN);
    }
}
