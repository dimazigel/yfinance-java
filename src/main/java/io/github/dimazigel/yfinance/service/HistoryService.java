package io.github.dimazigel.yfinance.service;

import io.github.dimazigel.yfinance.api.ChartApi;
import io.github.dimazigel.yfinance.enums.EventType;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.mapper.ChartMapper;
import io.github.dimazigel.yfinance.mapper.PriceBarResampler;
import io.github.dimazigel.yfinance.model.PriceHistory;
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

    public PriceHistory getHistory(HistoryRequest request) {
        if (request.interval() != Interval.THIRTY_MINUTES) {
            return fetch(request, request.interval());
        }
        PriceHistory fine;
        try {
            fine = fetch(request, Interval.FIFTEEN_MINUTES);
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

    private PriceHistory fetch(HistoryRequest request, Interval interval) {
        String events = request.events().stream()
                .map(EventType::wireValue)
                .collect(Collectors.joining(","));
        // A request carries either an explicit period (start[, end]) or a range, never both. Yahoo
        // rejects period1 without period2, so an open-ended window ends now.
        Instant start = request.start();
        Instant end = request.end() != null ? request.end() : clock.instant();
        Range range = request.range();
        var response = api.chart(
                request.symbol().value(),
                interval.wireValue(),
                start == null && range != null ? range.wireValue() : null,
                start != null ? start.getEpochSecond() : null,
                start != null ? end.getEpochSecond() : null,
                request.includePrePost(),
                events.isEmpty() ? null : events);
        return ChartMapper.toPriceHistory(response, request.symbol());
    }
}
