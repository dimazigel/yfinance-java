package io.github.dimazigel.yfinance.internal.service;

import io.github.dimazigel.yfinance.enums.EventType;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.internal.api.ChartApi;
import io.github.dimazigel.yfinance.internal.mapper.ChartMapper;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.market.HistoryQuery;
import io.github.dimazigel.yfinance.market.PriceBarResampler;
import io.github.dimazigel.yfinance.market.PriceHistory;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Retrieves and maps price history from the chart endpoint.
 *
 * <p>30m bars are fetched as 15m and resampled, because Yahoo returns 60m bars when asked for 30m
 * (the same workaround Python yfinance applies).
 */
public final class HistoryService {

    private final ChartApi api;
    private final Clock clock;

    public HistoryService(ChartApi api) {
        this(api, Clock.systemUTC());
    }

    public HistoryService(ChartApi api, Clock clock) {
        this.api = Objects.requireNonNull(api, "api");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PriceHistory getHistory(Symbol symbol, HistoryQuery query) {
        try (var ignored = LogContext.scope("history", symbol)) {
            if (query.interval() != Interval.THIRTY_MINUTES) {
                return fetch(symbol, query, query.interval());
            }
            PriceHistory fine;
            try {
                fine = fetch(symbol, query, Interval.FIFTEEN_MINUTES);
            } catch (YFDataException e) {
                // Yahoo's message names the interval actually fetched, which the caller never asked for.
                throw new YFDataException(e.getMessage() + " (30m resampled from 15m)", e);
            }
            return new PriceHistory(
                    fine.metadata(),
                    PriceBarResampler.resample(fine.bars(), Duration.ofMinutes(30)),
                    fine.dividends(),
                    fine.splits(),
                    fine.capitalGains());
        }
    }

    private PriceHistory fetch(Symbol symbol, HistoryQuery query, Interval interval) {
        String events = query.events().stream()
                .map(EventType::wireValue)
                .collect(Collectors.joining(","));
        // A query carries either an explicit period (start[, end]) or a range, never both. Yahoo
        // rejects period1 without period2, so an open-ended window ends now.
        Instant start = query.start().orElse(null);
        Instant end = query.end().orElse(clock.instant());
        Range range = query.range().orElse(null);
        var response = api.chart(
                symbol.value(),
                interval.wireValue(),
                start == null && range != null ? range.wireValue() : null,
                start != null ? start.getEpochSecond() : null,
                start != null ? end.getEpochSecond() : null,
                query.includePrePost(),
                events.isEmpty() ? null : events);
        return ChartMapper.toPriceHistory(response, symbol);
    }
}
