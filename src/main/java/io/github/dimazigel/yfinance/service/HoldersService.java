package io.github.dimazigel.yfinance.service;

import io.github.dimazigel.yfinance.enums.QuoteSummaryModule;
import io.github.dimazigel.yfinance.mapper.HoldersMapper;
import io.github.dimazigel.yfinance.mapper.QuoteSummaryMapper;
import io.github.dimazigel.yfinance.model.Holders;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.Objects;

/** Retrieves ownership and insider data. */
public final class HoldersService {

    private static final List<QuoteSummaryModule> MODULES = List.of(
            QuoteSummaryModule.MAJOR_HOLDERS_BREAKDOWN,
            QuoteSummaryModule.INSTITUTION_OWNERSHIP,
            QuoteSummaryModule.FUND_OWNERSHIP,
            QuoteSummaryModule.INSIDER_HOLDERS,
            QuoteSummaryModule.INSIDER_TRANSACTIONS,
            QuoteSummaryModule.NET_SHARE_PURCHASE_ACTIVITY);

    private final QuoteService quoteService;

    public HoldersService(QuoteService quoteService) {
        this.quoteService = Objects.requireNonNull(quoteService, "quoteService");
    }

    public Holders getHolders(Symbol symbol) {
        var response = quoteService.fetch(symbol, MODULES);
        return HoldersMapper.toHolders(QuoteSummaryMapper.requireResult(response, symbol));
    }
}
