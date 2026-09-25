package io.ziggy.yfinance.mapper;

import io.ziggy.yfinance.dto.timeseries.TimeseriesResponse;
import io.ziggy.yfinance.dto.timeseries.TimeseriesResponse.DataPoint;
import io.ziggy.yfinance.dto.timeseries.TimeseriesResponse.Result;
import io.ziggy.yfinance.enums.Frequency;
import io.ziggy.yfinance.enums.StatementType;
import io.ziggy.yfinance.exception.YFDataException;
import io.ziggy.yfinance.model.FinancialStatement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Maps the raw timeseries response into a {@link FinancialStatement}. */
public final class FundamentalsMapper {

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
                            continue; // absent or malformed period: skip the point, keep the statement
                        }
                        periods.add(date);
                        if (point.reportedValue().raw() != null) {
                            row.put(date, point.reportedValue().raw());
                        }
                    }
                }
            }
        }
        return new FinancialStatement(type, frequency, List.copyOf(periods), lineItems);
    }

    private static String stripPrefix(String key, String prefix) {
        return key.startsWith(prefix) ? key.substring(prefix.length()) : key;
    }
}
