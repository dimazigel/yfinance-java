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
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /**
     * Several statements for {@code equity} in <em>one</em> timeseries request: every requested
     * {@link StatementType} at every requested {@link Frequency}, split client-side by frequency
     * prefix and statement key set. {@link Frequency#TRAILING} × {@link StatementType#BALANCE_SHEET}
     * (which Yahoo does not publish) is skipped, not an error, when other pairs remain.
     *
     * @param equity the equity, the compile-time proof (see {@link #getStatement(Equity, StatementType, Frequency)})
     * @param types the statements wanted; not empty
     * @param frequencies the frequencies wanted; not empty
     * @return statement type → frequency → statement, unmodifiable, one entry per requested pair
     *     that Yahoo can serve (so no {@code BALANCE_SHEET → TRAILING} entry)
     * @throws IllegalArgumentException when either set is empty, or when the only pair is the
     *     trailing balance sheet
     */
    public Map<StatementType, Map<Frequency, FinancialStatement>> getStatements(
            Equity equity, Set<StatementType> types, Set<Frequency> frequencies) {
        return getStatements(equity.symbol(), types, frequencies);
    }

    /** Package-private: reused by tests and the live drift check, which don't hold an {@link Equity}. */
    Map<StatementType, Map<Frequency, FinancialStatement>> getStatements(
            Symbol symbol, Set<StatementType> types, Set<Frequency> frequencies) {
        requireServablePairs(types, frequencies);
        var orderedTypes = EnumSet.copyOf(types);
        var orderedFrequencies = EnumSet.copyOf(frequencies);
        var wireKeys = new LinkedHashSet<String>();
        for (StatementType type : orderedTypes) {
            for (Frequency frequency : orderedFrequencies) {
                if (servable(type, frequency)) {
                    for (String key : FundamentalKeys.forStatement(type)) {
                        wireKeys.add(frequency.wireValue() + key);
                    }
                }
            }
        }
        try (var ignored = LogContext.scope("statements", symbol)) {
            long now = clock.instant().getEpochSecond();
            var response = api.timeseries(symbol.value(), String.join(",", wireKeys), PERIOD_START, now);
            var byType = new EnumMap<StatementType, Map<Frequency, FinancialStatement>>(StatementType.class);
            for (StatementType type : orderedTypes) {
                var byFrequency = new EnumMap<Frequency, FinancialStatement>(Frequency.class);
                for (Frequency frequency : orderedFrequencies) {
                    if (servable(type, frequency)) {
                        byFrequency.put(frequency,
                                FundamentalsMapper.toStatement(response, type, frequency, FundamentalKeys.forStatement(type)));
                    }
                }
                if (!byFrequency.isEmpty()) {
                    byType.put(type, Collections.unmodifiableMap(byFrequency));
                }
            }
            return Collections.unmodifiableMap(byType);
        }
    }

    /**
     * The argument check of {@link #getStatements(Equity, Set, Set)}, exposed so the facade's batch
     * forms can fail fast before fanning out: a caller's programming error must be one exception,
     * not one {@code Failed} outcome per equity.
     *
     * @param types the statements wanted
     * @param frequencies the frequencies wanted
     * @throws IllegalArgumentException when either set is empty, or when no pair is servable (the
     *     only combination is the trailing balance sheet)
     */
    public static void requireServablePairs(Set<StatementType> types, Set<Frequency> frequencies) {
        if (types.isEmpty()) {
            throw new IllegalArgumentException("types must not be empty");
        }
        if (frequencies.isEmpty()) {
            throw new IllegalArgumentException("frequencies must not be empty");
        }
        for (StatementType type : types) {
            for (Frequency frequency : frequencies) {
                if (servable(type, frequency)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Yahoo has no trailing balance sheet; use ANNUAL or QUARTERLY");
    }

    /** Yahoo publishes trailing-twelve-month figures for flow statements only, never a balance sheet. */
    private static boolean servable(StatementType type, Frequency frequency) {
        return !(frequency == Frequency.TRAILING && type == StatementType.BALANCE_SHEET);
    }
}
