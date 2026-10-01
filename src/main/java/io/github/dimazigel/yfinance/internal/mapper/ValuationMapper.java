package io.github.dimazigel.yfinance.internal.mapper;

import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.fundamentals.ValuationMeasures;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.internal.dto.timeseries.TimeseriesResponse;
import io.github.dimazigel.yfinance.internal.dto.timeseries.TimeseriesResponse.DataPoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps the valuation-measure series of the timeseries endpoint into {@link ValuationMeasures} rows. */
public final class ValuationMapper {

    private static final Logger LOG = LoggerFactory.getLogger(ValuationMapper.class);

    private static final String MARKET_CAP = "MarketCap";
    private static final String ENTERPRISE_VALUE = "EnterpriseValue";
    private static final String PE_RATIO = "PeRatio";
    private static final String FORWARD_PE_RATIO = "ForwardPeRatio";
    private static final String PEG_RATIO = "PegRatio";
    private static final String PS_RATIO = "PsRatio";
    private static final String PB_RATIO = "PbRatio";
    private static final String EV_REVENUE_RATIO = "EnterprisesValueRevenueRatio";
    private static final String EV_EBITDA_RATIO = "EnterprisesValueEBITDARatio";

    /** The un-prefixed timeseries keys of the nine measures; the {@link Frequency} prefix is applied per request. */
    public static final List<String> KEYS = List.of(
            MARKET_CAP, ENTERPRISE_VALUE, PE_RATIO, FORWARD_PE_RATIO, PEG_RATIO, PS_RATIO, PB_RATIO,
            EV_REVENUE_RATIO, EV_EBITDA_RATIO);

    private ValuationMapper() {}

    /**
     * One row per period end that has at least one measure, oldest first. A point without a value
     * is no point; a point without a usable date is skipped (DEBUG). Results without series — what
     * Yahoo answers for a non-equity or a symbol it does not know — give an empty list.
     */
    public static List<ValuationMeasures> toList(List<TimeseriesResponse.Result> results, Frequency frequency) {
        String prefix = frequency.wireValue();
        var byDate = new TreeMap<LocalDate, Map<String, DataPoint>>();
        int skipped = 0;
        for (TimeseriesResponse.Result result : results) {
            for (var entry : result.series().entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith(prefix) || !KEYS.contains(key.substring(prefix.length()))) {
                    continue;
                }
                String measure = key.substring(prefix.length());
                for (DataPoint point : entry.getValue()) {
                    if (point == null || point.reportedValue() == null || point.reportedValue().raw() == null) {
                        continue;
                    }
                    LocalDate date = MapperSupport.localDate(point.asOfDate());
                    if (date == null) {
                        skipped++;
                        continue;
                    }
                    byDate.computeIfAbsent(date, d -> new HashMap<>()).put(measure, point);
                }
            }
        }
        if (skipped > 0) {
            LOG.atDebug().addKeyValue("skipped", skipped)
                    .log("Skipped {} valuation {} without a usable date", skipped, skipped == 1 ? "point" : "points");
        }
        var rows = new ArrayList<ValuationMeasures>();
        byDate.forEach((date, points) -> rows.add(new ValuationMeasures(
                date,
                currency(points),
                value(points, MARKET_CAP),
                value(points, ENTERPRISE_VALUE),
                value(points, PE_RATIO),
                value(points, FORWARD_PE_RATIO),
                value(points, PEG_RATIO),
                value(points, PS_RATIO),
                value(points, PB_RATIO),
                value(points, EV_REVENUE_RATIO),
                value(points, EV_EBITDA_RATIO))));
        return List.copyOf(rows);
    }

    private static Optional<BigDecimal> value(Map<String, DataPoint> points, String measure) {
        return Optional.ofNullable(points.get(measure)).map(DataPoint::reportedValue).map(TimeseriesResponse.ReportedValue::raw);
    }

    /** The currency Yahoo states on the row's money values: market cap first, enterprise value otherwise. */
    private static Optional<QuoteCurrency> currency(Map<String, DataPoint> points) {
        return Optional.ofNullable(points.get(MARKET_CAP)).map(DataPoint::currencyCode)
                .or(() -> Optional.ofNullable(points.get(ENTERPRISE_VALUE)).map(DataPoint::currencyCode))
                .filter(code -> !code.isBlank())
                .map(QuoteCurrency::of);
    }
}
