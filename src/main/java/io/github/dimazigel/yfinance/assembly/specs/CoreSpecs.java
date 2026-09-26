package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.optional;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.EPOCH_MILLIS;
import static io.github.dimazigel.yfinance.assembly.Unit.EPOCH_SECONDS;
import static io.github.dimazigel.yfinance.assembly.Unit.PERCENT;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Appendix A, "Universal core": present for every asset class. */
public final class CoreSpecs {

    private CoreSpecs() {}

    public static final List<FieldSpec> CORE = List.of(
            required("symbol", RAW, "v7:symbol", "qs:quoteType.symbol"),
            required("shortName", RAW, "v7:shortName", "qs:quoteType.shortName", "qs:price.shortName"),
            optional("longName", RAW, "v7:longName", "qs:quoteType.longName", "qs:price.longName"),
            required("currency", RAW, "v7:currency", "qs:price.currency", "qs:summaryDetail.currency"),
            required("exchange", RAW, "v7:exchange", "qs:quoteType.exchange", "qs:price.exchange"),
            required("fullExchangeName", RAW, "v7:fullExchangeName", "qs:price.exchangeName"),
            required("exchangeTimezone", RAW, "v7:exchangeTimezoneName", "qs:quoteType.timeZoneFullName"),
            required("marketState", RAW, "v7:marketState", "qs:price.marketState"),
            required("price", RAW, "v7:regularMarketPrice", "qs:price.regularMarketPrice"),
            required("change", RAW, "v7:regularMarketChange", "qs:price.regularMarketChange"),
            required("changePercent", PERCENT, "v7:regularMarketChangePercent", "qs:price.regularMarketChangePercent"),
            required("previousClose", RAW, "v7:regularMarketPreviousClose", "qs:price.regularMarketPreviousClose", "qs:summaryDetail.previousClose"),
            required("priceTime", EPOCH_SECONDS, "v7:regularMarketTime", "qs:price.regularMarketTime"),
            required("fiftyTwoWeekLow", RAW, "v7:fiftyTwoWeekLow", "qs:summaryDetail.fiftyTwoWeekLow"),
            required("fiftyTwoWeekHigh", RAW, "v7:fiftyTwoWeekHigh", "qs:summaryDetail.fiftyTwoWeekHigh"),
            required("fiftyDayAverage", RAW, "v7:fiftyDayAverage", "qs:summaryDetail.fiftyDayAverage"),
            required("twoHundredDayAverage", RAW, "v7:twoHundredDayAverage", "qs:summaryDetail.twoHundredDayAverage"),
            required("averageVolume10Day", RAW, "v7:averageDailyVolume10Day", "qs:summaryDetail.averageDailyVolume10Day", "qs:price.averageDailyVolume10Day"),
            required("averageVolume3Month", RAW, "v7:averageDailyVolume3Month", "qs:summaryDetail.averageVolume", "qs:price.averageDailyVolume3Month"),
            required("firstTradeDate", EPOCH_MILLIS, "v7:firstTradeDateMilliseconds"),
            required("priceHint", RAW, "v7:priceHint", "qs:price.priceHint"),
            required("hasPrePostMarketData", RAW, "v7:hasPrePostMarketData"));
}
