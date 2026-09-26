package io.github.dimazigel.yfinance.service;

import io.github.dimazigel.yfinance.api.FundamentalsApi;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.fundamentals.FinancialStatement;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.mapper.FundamentalsMapper;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.stream.Collectors;

/** Retrieves income, balance-sheet and cash-flow statements via the timeseries endpoint. */
public final class FundamentalsService {

    // Yahoo caps at ~4 years / 5 quarters regardless; this lower bound mirrors yfinance.
    private static final long PERIOD_START = LocalDate.of(2016, 12, 31).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

    private final FundamentalsApi api;
    private final Clock clock;

    public FundamentalsService(FundamentalsApi api) {
        this(api, Clock.systemUTC());
    }

    public FundamentalsService(FundamentalsApi api, Clock clock) {
        this.api = Objects.requireNonNull(api, "api");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * One financial statement for {@code equity} at the given frequency. Statements are
     * equities-only (Yahoo's timeseries endpoint returns empty series for every other class); the
     * {@link Equity} parameter is the compile-time proof, so there is deliberately no overload for
     * {@code Etf}, {@code MutualFund}, or any other {@link io.github.dimazigel.yfinance.instrument.Instrument}.
     *
     * @throws IllegalArgumentException for {@link Frequency#TRAILING} with
     *     {@link StatementType#BALANCE_SHEET}: Yahoo only publishes trailing-twelve-month figures
     *     for flow statements (income and cash flow), never for a point-in-time balance sheet
     */
    public FinancialStatement getStatement(Equity equity, StatementType type, Frequency frequency) {
        return getStatement(equity.symbol(), type, frequency);
    }

    /** Package-private: reused by tests and the live drift check, which don't hold an {@link Equity}. */
    FinancialStatement getStatement(Symbol symbol, StatementType type, Frequency frequency) {
        if (frequency == Frequency.TRAILING && type == StatementType.BALANCE_SHEET) {
            throw new IllegalArgumentException(
                    "Yahoo has no trailing balance sheet; use ANNUAL or QUARTERLY for " + symbol);
        }
        try (var ignored = LogContext.scope("statements", symbol)) {
            String typeParam = FundamentalKeys.forStatement(type).stream()
                    .map(key -> frequency.wireValue() + key)
                    .collect(Collectors.joining(","));
            long now = clock.instant().getEpochSecond();
            var response = api.timeseries(symbol.value(), typeParam, PERIOD_START, now);
            return FundamentalsMapper.toStatement(response, type, frequency);
        }
    }
}
