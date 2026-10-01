package io.github.dimazigel.yfinance.service;

import io.github.dimazigel.yfinance.api.FundamentalsApi;
import io.github.dimazigel.yfinance.dto.timeseries.TimeseriesResponse;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.fundamentals.FinancialStatement;
import io.github.dimazigel.yfinance.fundamentals.SharesOutstanding;
import io.github.dimazigel.yfinance.fundamentals.ValuationMeasures;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.mapper.FundamentalsMapper;
import io.github.dimazigel.yfinance.mapper.SharesMapper;
import io.github.dimazigel.yfinance.mapper.ValuationMapper;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Retrieves statements, shares-outstanding history and valuation-measure history via the timeseries endpoint. */
public final class FundamentalsService {

    // Yahoo caps at ~4 years / 5 quarters regardless; this lower bound mirrors yfinance.
    private static final long PERIOD_START = LocalDate.of(2016, 12, 31).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

    /**
     * Maximum distinct timeseries keys per {@code type=} request. A single statement at one
     * frequency (~100-150 {@link io.github.dimazigel.yfinance.enums.LineItem} keys) fits in one
     * request; the multi-statement form ({@link #getStatements}) can exceed this and is split into
     * several requests, merged client-side. Verified live against Yahoo (batch E/1).
     */
    static final int MAX_KEYS_PER_REQUEST = 150;

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
            List<String> wireKeys = FundamentalKeys.forStatement(type).stream()
                    .map(key -> frequency.wireValue() + key)
                    .toList();
            var merged = merge(fetchSeries(symbol, wireKeys));
            return FundamentalsMapper.toStatement(merged, type, frequency);
        }
    }

    /**
     * Several statements for {@code equity} in as few timeseries requests as the key count allows
     * (≤ 150 keys each; a single statement is one request, the full 3×3 form is seven): every
     * requested {@link StatementType} at every requested {@link Frequency}, split client-side by
     * frequency prefix and statement key set. {@link Frequency#TRAILING} × {@link StatementType#BALANCE_SHEET}
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
            var merged = merge(fetchSeries(symbol, List.copyOf(wireKeys)));
            var byType = new EnumMap<StatementType, Map<Frequency, FinancialStatement>>(StatementType.class);
            for (StatementType type : orderedTypes) {
                var byFrequency = new EnumMap<Frequency, FinancialStatement>(Frequency.class);
                for (Frequency frequency : orderedFrequencies) {
                    if (servable(type, frequency)) {
                        byFrequency.put(frequency,
                                FundamentalsMapper.toStatement(merged, type, frequency, FundamentalKeys.forStatement(type)));
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
     * Historical shares-outstanding reports for {@code equity} over {@code [start, end]} (batch
     * E/1, {@code get_shares_full} parity). Yahoo may report two values for one date; both are kept,
     * in wire order. Unlike statements, this is tolerant of absence: a symbol Yahoo has no history
     * for (e.g. a non-equity, were this called through a raw endpoint test) comes back as an empty
     * list, never a {@link YFDataException}.
     *
     * @param equity the equity, the compile-time proof (see {@link #getStatement(Equity, StatementType, Frequency)})
     * @param start the window start (inclusive, Yahoo's own {@code period1} semantics)
     * @param end the window end (inclusive, Yahoo's own {@code period2} semantics)
     * @throws IllegalArgumentException if {@code start} is not before {@code end}
     */
    public List<SharesOutstanding> getSharesOutstanding(Equity equity, Instant start, Instant end) {
        return getSharesOutstanding(equity.symbol(), start, end);
    }

    /** Package-private: reused by tests and the live drift check, which don't hold an {@link Equity}. */
    List<SharesOutstanding> getSharesOutstanding(Symbol symbol, Instant start, Instant end) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start (" + start + ") must be before end (" + end + ") for " + symbol);
        }
        try (var ignored = LogContext.scope("shares", symbol)) {
            var response = api.sharesOutstanding(symbol.value(), start.getEpochSecond(), end.getEpochSecond());
            return SharesMapper.toList(response);
        }
    }

    /**
     * {@code equity}'s valuation measures at each recent period end, oldest first, in one request
     * (batch E/2, Python yfinance's {@code Ticker.valuation}): {@link Frequency#QUARTERLY} gives
     * the latest five quarter ends, {@link Frequency#ANNUAL} the latest four fiscal year ends —
     * Yahoo serves no more per request. Tolerant of absence like
     * {@link #getSharesOutstanding(Equity, Instant, Instant)}: no series is an empty list.
     *
     * @param equity the equity, the compile-time proof (see {@link #getStatement(Equity, StatementType, Frequency)})
     * @param frequency {@link Frequency#QUARTERLY} or {@link Frequency#ANNUAL}
     * @throws IllegalArgumentException for {@link Frequency#TRAILING}: Yahoo's trailing series is
     *     an irregular set of snapshot dates that differ per measure, not a history by period, and
     *     the current values are on the snapshot and detail records
     */
    public List<ValuationMeasures> getValuationHistory(Equity equity, Frequency frequency) {
        return getValuationHistory(equity.symbol(), frequency);
    }

    /** Package-private: reused by tests and the live drift check, which don't hold an {@link Equity}. */
    List<ValuationMeasures> getValuationHistory(Symbol symbol, Frequency frequency) {
        if (frequency == Frequency.TRAILING) {
            throw new IllegalArgumentException(
                    "Valuation history is stated per period end; use QUARTERLY or ANNUAL for " + symbol);
        }
        try (var ignored = LogContext.scope("valuation", symbol)) {
            List<String> wireKeys = ValuationMapper.KEYS.stream().map(key -> frequency.wireValue() + key).toList();
            return ValuationMapper.toList(fetchSeries(symbol, wireKeys), frequency);
        }
    }

    /**
     * Fetches {@code wireKeys} in chunks of at most {@link #MAX_KEYS_PER_REQUEST}, issuing one
     * {@code api.timeseries(...)} call per chunk and concatenating the {@code result} lists
     * (null-safe). A failed chunk fails the whole call — never a partial statement. The request
     * order is deterministic: callers build {@code wireKeys} in statement order, then frequency
     * order, then {@link io.github.dimazigel.yfinance.enums.LineItem} declaration order.
     */
    private List<TimeseriesResponse.Result> fetchSeries(Symbol symbol, List<String> wireKeys) {
        long now = clock.instant().getEpochSecond();
        var merged = new ArrayList<TimeseriesResponse.Result>();
        for (int start = 0; start < wireKeys.size(); start += MAX_KEYS_PER_REQUEST) {
            List<String> chunk = wireKeys.subList(start, Math.min(start + MAX_KEYS_PER_REQUEST, wireKeys.size()));
            var response = api.timeseries(symbol.value(), String.join(",", chunk), PERIOD_START, now);
            var ts = response.timeseries();
            if (ts == null) {
                throw new YFDataException("Malformed timeseries response");
            }
            if (ts.error() != null) {
                throw new YFDataException("Yahoo timeseries error: " + ts.error().description());
            }
            if (ts.result() != null) {
                merged.addAll(ts.result());
            }
        }
        return merged;
    }

    /** Wraps already-validated, merged results back into a {@link TimeseriesResponse} for the mapper. */
    private static TimeseriesResponse merge(List<TimeseriesResponse.Result> results) {
        return new TimeseriesResponse(new TimeseriesResponse.Timeseries(results, null));
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
