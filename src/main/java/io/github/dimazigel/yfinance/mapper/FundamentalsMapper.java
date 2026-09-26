package io.github.dimazigel.yfinance.mapper;

import io.github.dimazigel.yfinance.dto.timeseries.TimeseriesResponse;
import io.github.dimazigel.yfinance.dto.timeseries.TimeseriesResponse.DataPoint;
import io.github.dimazigel.yfinance.dto.timeseries.TimeseriesResponse.Result;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.model.FinancialStatement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps the raw timeseries response into a {@link FinancialStatement}. */
public final class FundamentalsMapper {

    private static final Logger LOG = LoggerFactory.getLogger(FundamentalsMapper.class);

    private FundamentalsMapper() {}

    public static FinancialStatement toStatement(
            TimeseriesResponse response, StatementType type, Frequency frequency) {
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
