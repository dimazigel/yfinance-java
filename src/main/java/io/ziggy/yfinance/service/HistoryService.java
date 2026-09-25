package io.ziggy.yfinance.service;

import io.ziggy.yfinance.api.ChartApi;
import io.ziggy.yfinance.enums.EventType;
import io.ziggy.yfinance.enums.Interval;
import io.ziggy.yfinance.exception.YFDataException;
import io.ziggy.yfinance.mapper.ChartMapper;
import io.ziggy.yfinance.mapper.PriceBarResampler;
import io.ziggy.yfinance.model.PriceHistory;
import java.time.Duration;
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

    public HistoryService(ChartApi api) {
        this.api = Objects.requireNonNull(api, "api");
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
        var response = api.chart(
                request.symbol().value(),
                interval.wireValue(),
                request.hasPeriod() ? null : request.range().wireValue(),
                request.hasPeriod() ? request.start().getEpochSecond() : null,
                request.hasPeriod() && request.end() != null ? request.end().getEpochSecond() : null,
                request.includePrePost(),
                events.isEmpty() ? null : events);
        return ChartMapper.toPriceHistory(response, request.symbol());
    }
}
