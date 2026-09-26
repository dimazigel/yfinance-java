package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Regular intraday session: open, dayLow, dayHigh, volume. */
public final class SessionSpecs {

    private SessionSpecs() {}

    public static final List<FieldSpec> SESSION = List.of(
            required("open", RAW, "v7:regularMarketOpen", "qs:price.regularMarketOpen", "qs:summaryDetail.open"),
            required("dayLow", RAW, "v7:regularMarketDayLow", "qs:price.regularMarketDayLow", "qs:summaryDetail.dayLow"),
            required("dayHigh", RAW, "v7:regularMarketDayHigh", "qs:price.regularMarketDayHigh", "qs:summaryDetail.dayHigh"),
            required("volume", RAW, "v7:regularMarketVolume", "qs:price.regularMarketVolume", "qs:summaryDetail.volume"));
}
