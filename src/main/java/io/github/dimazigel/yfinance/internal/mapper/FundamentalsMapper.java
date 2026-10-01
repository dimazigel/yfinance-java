package io.github.dimazigel.yfinance.internal.mapper;

import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.fundamentals.FinancialStatement;
import io.github.dimazigel.yfinance.internal.dto.timeseries.TimeseriesResponse;
import io.github.dimazigel.yfinance.internal.dto.timeseries.TimeseriesResponse.DataPoint;
import io.github.dimazigel.yfinance.internal.dto.timeseries.TimeseriesResponse.Result;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps the raw timeseries response into a {@link FinancialStatement}. */
public final class FundamentalsMapper {

    private static final Logger LOG = LoggerFactory.getLogger(FundamentalsMapper.class);

    private FundamentalsMapper() {}

    /**
     * Every series in {@code response} as one statement: each key is stripped of {@code frequency}'s
     * prefix (a key with no such prefix is kept as is). For a response that answered a
     * single-statement request.
     */
    public static FinancialStatement toStatement(
            TimeseriesResponse response, StatementType type, Frequency frequency) {
        return toStatement(response, type, frequency, key -> true);
    }

    /**
     * The {@code (type, frequency)} slice of a response that answered a request for several
     * statements at once: only series whose key is {@code frequency.wireValue() + k} with
     * {@code k} in {@code keys} are included, so the periods and line items of one slice never
     * leak into another.
     */
    public static FinancialStatement toStatement(
            TimeseriesResponse response, StatementType type, Frequency frequency, Collection<String> keys) {
        String prefix = frequency.wireValue();
        Set<String> wanted = Set.copyOf(keys);
        return toStatement(response, type, frequency,
                key -> key.startsWith(prefix) && wanted.contains(key.substring(prefix.length())));
    }

    private static FinancialStatement toStatement(
            TimeseriesResponse response, StatementType type, Frequency frequency, Predicate<String> includeKey) {
        var ts = response.timeseries();
        if (ts == null) {
            throw new YFDataException("Malformed timeseries response");
        }
        if (ts.error() != null) {
            throw new YFDataException("Yahoo timeseries error: " + ts.error().description());
        }

        var lineItems = new LinkedHashMap<String, Map<LocalDate, BigDecimal>>();
        var periods = new TreeSet<LocalDate>();
        String prefix = frequency.wireValue();
        int skipped = 0;

        if (ts.result() != null) {
            for (Result result : ts.result()) {
                for (var entry : result.series().entrySet()) {
                    if (!includeKey.test(entry.getKey())) {
                        continue;
                    }
                    String lineItem = stripPrefix(entry.getKey(), prefix);
                    var row = lineItems.computeIfAbsent(lineItem, k -> new LinkedHashMap<>());
                    for (DataPoint point : entry.getValue()) {
                        if (point == null || point.reportedValue() == null) {
                            continue;
                        }
                        LocalDate date = MapperSupport.localDate(point.asOfDate());
                        if (date == null) {
                            skipped++; // absent or malformed period: skip the point, keep the statement
                            continue;
                        }
                        periods.add(date);
                        if (point.reportedValue().raw() != null) {
                            row.put(date, point.reportedValue().raw());
                        }
                    }
                }
            }
        }
        if (skipped > 0) {
            LOG.atDebug().addKeyValue("skipped", skipped)
                    .log("Skipped {} fundamentals {} without a usable date", skipped, skipped == 1 ? "point" : "points");
        }
        return new FinancialStatement(type, frequency, List.copyOf(periods), lineItems);
    }

    private static String stripPrefix(String key, String prefix) {
        return key.startsWith(prefix) ? key.substring(prefix.length()) : key;
    }
}
