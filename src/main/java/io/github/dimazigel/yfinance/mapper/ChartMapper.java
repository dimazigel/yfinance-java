package io.github.dimazigel.yfinance.mapper;

import io.github.dimazigel.yfinance.dto.chart.ChartResponse;
import io.github.dimazigel.yfinance.dto.chart.ChartResponse.AdjClose;
import io.github.dimazigel.yfinance.dto.chart.ChartResponse.ChartEvents;
import io.github.dimazigel.yfinance.dto.chart.ChartResponse.ChartMeta;
import io.github.dimazigel.yfinance.dto.chart.ChartResponse.ChartResult;
import io.github.dimazigel.yfinance.dto.chart.ChartResponse.CurrentTradingPeriod;
import io.github.dimazigel.yfinance.dto.chart.ChartResponse.Quote;
import io.github.dimazigel.yfinance.dto.chart.ChartResponse.TradingPeriod;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.model.CapitalGain;
import io.github.dimazigel.yfinance.model.Dividend;
import io.github.dimazigel.yfinance.model.HistoryMetadata;
import io.github.dimazigel.yfinance.model.PriceBar;
import io.github.dimazigel.yfinance.model.PriceHistory;
import io.github.dimazigel.yfinance.model.Split;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps the raw {@link ChartResponse} into the clean {@link PriceHistory} model. */
public final class ChartMapper {

    private ChartMapper() {}

    public static PriceHistory toPriceHistory(ChartResponse response, Symbol requested) {
        var chart = response.chart();
        if (chart == null) {
            throw new YFDataException("Malformed chart response for " + requested);
        }
        ChartResult result = MapperSupport.firstResult(chart.result(), chart.error(), "chart data", requested);
        return new PriceHistory(
                mapMetadata(result.meta(), requested),
                mapBars(result),
                mapDividends(result.events()),
                mapSplits(result.events()),
                mapCapitalGains(result.events()));
    }

    private static HistoryMetadata mapMetadata(@Nullable ChartMeta meta, Symbol requested) {
        if (meta == null) {
            return new HistoryMetadata(requested, null, null, null, null, null, null, null, null,
                    null, null, null, List.of(), null, null);
        }
        return new HistoryMetadata(
                MapperSupport.symbolOr(meta.symbol(), requested),
                MapperSupport.currency(meta.currency()),
                meta.exchangeName(),
                meta.fullExchangeName(),
                meta.instrumentType(),
                MapperSupport.zoneId(meta.exchangeTimezoneName()),
                MapperSupport.epochSecond(meta.firstTradeDate()),
                meta.regularMarketPrice(),
                meta.chartPreviousClose(),
                MapperSupport.epochSecond(meta.regularMarketTime()),
                meta.priceHint(),
                MapperSupport.interval(meta.dataGranularity()),
                MapperSupport.ranges(meta.validRanges()),
                mapTradingPeriods(meta.currentTradingPeriod()),
                meta.hasPrePostMarketData());
    }

    /** All three sessions or nothing: a partial day is not a usable trading calendar. */
    private static HistoryMetadata.@Nullable TradingPeriods mapTradingPeriods(@Nullable CurrentTradingPeriod ctp) {
        if (ctp == null) {
            return null;
        }
        var pre = mapTradingPeriod(ctp.pre());
        var regular = mapTradingPeriod(ctp.regular());
        var post = mapTradingPeriod(ctp.post());
        if (pre == null || regular == null || post == null) {
            return null;
        }
        return new HistoryMetadata.TradingPeriods(pre, regular, post);
    }

    private static HistoryMetadata.@Nullable TradingPeriod mapTradingPeriod(@Nullable TradingPeriod p) {
        if (p == null || p.start() == null || p.end() == null) {
            return null;
        }
        return new HistoryMetadata.TradingPeriod(Instant.ofEpochSecond(p.start()), Instant.ofEpochSecond(p.end()));
    }

    private static List<PriceBar> mapBars(ChartResult result) {
        var timestamps = result.timestamp();
        if (timestamps == null || result.indicators() == null
                || result.indicators().quote() == null
                || result.indicators().quote().isEmpty()) {
            return List.of();
        }
        Quote quote = result.indicators().quote().getFirst();
        List<AdjClose> adjCloses = result.indicators().adjclose();
        List<@Nullable BigDecimal> adjClose = adjCloses != null && !adjCloses.isEmpty()
                ? adjCloses.getFirst().adjclose()
                : null;

        var bars = new ArrayList<PriceBar>(timestamps.size());
        for (int i = 0; i < timestamps.size(); i++) {
            BigDecimal close = at(quote.close(), i);
            if (close == null) {
                // Yahoo pads intraday series with all-null rows (halts, pre-open); skip them.
                continue;
            }
            bars.add(new PriceBar(
                    Instant.ofEpochSecond(timestamps.get(i)),
                    at(quote.open(), i),
                    at(quote.high(), i),
                    at(quote.low(), i),
                    close,
                    at(adjClose, i),
                    at(quote.volume(), i)));
        }
        return bars;
    }

    private static List<Dividend> mapDividends(@Nullable ChartEvents events) {
        if (events == null || events.dividends() == null) {
            return List.of();
        }
        return events.dividends().values().stream()
                .map(d -> new Dividend(MapperSupport.epochSecond(d.date()), d.amount()))
                .sorted(Comparator.comparing(Dividend::date, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private static List<Split> mapSplits(@Nullable ChartEvents events) {
        if (events == null || events.splits() == null) {
            return List.of();
        }
        return events.splits().values().stream()
                .map(s -> new Split(MapperSupport.epochSecond(s.date()), s.numerator(), s.denominator(), s.splitRatio()))
                .sorted(Comparator.comparing(Split::date, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private static List<CapitalGain> mapCapitalGains(@Nullable ChartEvents events) {
        if (events == null || events.capitalGains() == null) {
            return List.of();
        }
        return events.capitalGains().values().stream()
                .map(c -> new CapitalGain(MapperSupport.epochSecond(c.date()), c.amount()))
                .sorted(Comparator.comparing(CapitalGain::date, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private static <T extends @Nullable Object> @Nullable T at(@Nullable List<T> list, int index) {
        return list != null && index < list.size() ? list.get(index) : null;
    }
}
