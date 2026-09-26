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
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.market.CapitalGain;
import io.github.dimazigel.yfinance.market.Dividend;
import io.github.dimazigel.yfinance.market.HistoryMetadata;
import io.github.dimazigel.yfinance.market.PriceBar;
import io.github.dimazigel.yfinance.market.PriceHistory;
import io.github.dimazigel.yfinance.market.Split;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps the raw {@link ChartResponse} into the clean {@link PriceHistory} model. */
public final class ChartMapper {

    private static final Logger LOG = LoggerFactory.getLogger(ChartMapper.class);

    /** Field names {@link #mapMetadata} reports as missing when {@code meta} itself is absent. */
    private static final List<String> REQUIRED_META_FIELDS = List.of(
            "currency", "exchangeName", "fullExchangeName", "instrumentType", "timezone", "firstTradeDate",
            "regularMarketPrice", "previousClose", "regularMarketTime", "priceHint", "currentTradingPeriod",
            "hasPrePostMarketData");

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

    /**
     * Builds the history metadata, or throws when any field but {@code dataGranularity} is absent:
     * unlike a quote or detail, a price history has no downgrade tier to fall back to.
     */
    private static HistoryMetadata mapMetadata(@Nullable ChartMeta meta, Symbol requested) {
        if (meta == null) {
            throw new YFDataException("chart metadata incomplete: " + String.join(", ", REQUIRED_META_FIELDS));
        }

        var missing = new ArrayList<String>();

        QuoteCurrency currency = currencyOrNull(meta.currency());
        if (currency == null) {
            missing.add("currency");
        }
        String exchangeName = meta.exchangeName();
        if (exchangeName == null) {
            missing.add("exchangeName");
        }
        String fullExchangeName = meta.fullExchangeName();
        if (fullExchangeName == null) {
            missing.add("fullExchangeName");
        }
        String instrumentType = meta.instrumentType();
        if (instrumentType == null) {
            missing.add("instrumentType");
        }
        ZoneId timezone = MapperSupport.zoneId(meta.exchangeTimezoneName());
        if (timezone == null) {
            missing.add("timezone");
        }
        Instant firstTradeDate = MapperSupport.epochSecond(meta.firstTradeDate());
        if (firstTradeDate == null) {
            missing.add("firstTradeDate");
        }
        BigDecimal regularMarketPrice = meta.regularMarketPrice();
        if (regularMarketPrice == null) {
            missing.add("regularMarketPrice");
        }
        BigDecimal previousClose = meta.chartPreviousClose();
        if (previousClose == null) {
            missing.add("previousClose");
        }
        Instant regularMarketTime = MapperSupport.epochSecond(meta.regularMarketTime());
        if (regularMarketTime == null) {
            missing.add("regularMarketTime");
        }
        Integer priceHint = meta.priceHint();
        if (priceHint == null) {
            missing.add("priceHint");
        }
        HistoryMetadata.TradingPeriods currentTradingPeriod = mapTradingPeriods(meta.currentTradingPeriod());
        if (currentTradingPeriod == null) {
            missing.add("currentTradingPeriod");
        }
        Boolean hasPrePostMarketData = meta.hasPrePostMarketData();
        if (hasPrePostMarketData == null) {
            missing.add("hasPrePostMarketData");
        }

        if (!missing.isEmpty()) {
            throw new YFDataException("chart metadata incomplete: " + String.join(", ", missing));
        }

        return new HistoryMetadata(
                MapperSupport.symbolOr(meta.symbol(), requested),
                Objects.requireNonNull(currency), // missing names collected above
                Objects.requireNonNull(exchangeName),
                Objects.requireNonNull(fullExchangeName),
                Objects.requireNonNull(instrumentType),
                Objects.requireNonNull(timezone),
                Objects.requireNonNull(firstTradeDate),
                Objects.requireNonNull(regularMarketPrice),
                Objects.requireNonNull(previousClose),
                Objects.requireNonNull(regularMarketTime),
                Objects.requireNonNull(priceHint),
                Optional.ofNullable(MapperSupport.interval(meta.dataGranularity())),
                MapperSupport.ranges(meta.validRanges()),
                Objects.requireNonNull(currentTradingPeriod),
                Objects.requireNonNull(hasPrePostMarketData));
    }

    /** {@code null} unless {@code code} is a non-blank currency code (ISO or not, e.g. {@code GBp}). */
    private static @Nullable QuoteCurrency currencyOrNull(@Nullable String code) {
        return code == null || code.isBlank() ? null : QuoteCurrency.of(code);
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
        int dropped = 0;
        for (int i = 0; i < timestamps.size(); i++) {
            BigDecimal open = at(quote.open(), i);
            BigDecimal high = at(quote.high(), i);
            BigDecimal low = at(quote.low(), i);
            BigDecimal close = at(quote.close(), i);
            if (open == null || high == null || low == null || close == null) {
                // Yahoo pads intraday series with all-null rows (halts, pre-open) and occasionally
                // omits just one OHLC value; either way the candle is not usable.
                dropped++;
                continue;
            }
            bars.add(new PriceBar(
                    Instant.ofEpochSecond(timestamps.get(i)),
                    open, high, low, close,
                    Optional.ofNullable(at(adjClose, i)),
                    Optional.ofNullable(at(quote.volume(), i))));
        }
        if (dropped > 0) {
            LOG.atDebug().addKeyValue("dropped", dropped).addKeyValue("total", timestamps.size())
                    .log("Dropped {} of {} bars without a complete OHLC", dropped, timestamps.size());
        }
        return bars;
    }

    private static List<Dividend> mapDividends(@Nullable ChartEvents events) {
        if (events == null || events.dividends() == null) {
            return List.of();
        }
        return events.dividends().values().stream()
                .filter(d -> complete(d.date() != null && d.amount() != null, "dividend", d.date()))
                .map(d -> new Dividend(
                        Objects.requireNonNull(MapperSupport.epochSecond(d.date())), // filtered above
                        Objects.requireNonNull(d.amount()))) // filtered above
                .sorted(Comparator.comparing(Dividend::date))
                .toList();
    }

    private static List<Split> mapSplits(@Nullable ChartEvents events) {
        if (events == null || events.splits() == null) {
            return List.of();
        }
        return events.splits().values().stream()
                .filter(s -> complete(s.date() != null && s.numerator() != null && s.denominator() != null && s.splitRatio() != null, "split", s.date()))
                .map(s -> new Split(
                        Objects.requireNonNull(MapperSupport.epochSecond(s.date())), // filtered above
                        Objects.requireNonNull(s.numerator()), // filtered above
                        Objects.requireNonNull(s.denominator()), // filtered above
                        Objects.requireNonNull(s.splitRatio()))) // filtered above
                .sorted(Comparator.comparing(Split::date))
                .toList();
    }

    private static List<CapitalGain> mapCapitalGains(@Nullable ChartEvents events) {
        if (events == null || events.capitalGains() == null) {
            return List.of();
        }
        return events.capitalGains().values().stream()
                .filter(c -> complete(c.date() != null && c.amount() != null, "capital gain", c.date()))
                .map(c -> new CapitalGain(
                        Objects.requireNonNull(MapperSupport.epochSecond(c.date())), // filtered above
                        Objects.requireNonNull(c.amount()))) // filtered above
                .sorted(Comparator.comparing(CapitalGain::date))
                .toList();
    }

    /** Every sampled chart event carried its date and value, so an incomplete one is drift worth a DEBUG line. */
    private static boolean complete(boolean complete, String kind, @Nullable Object date) {
        if (!complete) {
            LOG.atDebug().addKeyValue("kind", kind).addKeyValue("date", date)
                    .log("Dropped a {} event without a complete date and value (date={})", kind, date);
        }
        return complete;
    }

    private static <T extends @Nullable Object> @Nullable T at(@Nullable List<T> list, int index) {
        return list != null && index < list.size() ? list.get(index) : null;
    }
}
