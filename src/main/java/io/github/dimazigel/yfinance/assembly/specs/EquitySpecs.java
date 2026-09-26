package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.clustered;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.optional;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.requiredCluster;
import static io.github.dimazigel.yfinance.assembly.Unit.EPOCH_SECONDS;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Equity snapshot: all guaranteed and optional fields for an Equity record. */
public final class EquitySpecs {

    private EquitySpecs() {}

    private static final List<FieldSpec> OWN = List.of(
            required("marketCap", RAW, "v7:marketCap", "qs:price.marketCap", "qs:summaryDetail.marketCap"),
            required("sharesOutstanding", RAW, "v7:sharesOutstanding", "qs:defaultKeyStatistics.sharesOutstanding"),
            required("impliedSharesOutstanding", RAW, "v7:impliedSharesOutstanding", "qs:defaultKeyStatistics.impliedSharesOutstanding"),
            required("financialCurrency", RAW, "v7:financialCurrency", "qs:financialData.financialCurrency"),
            requiredCluster("nextEarnings", "nextEarnings.expected", EPOCH_SECONDS, "v7:earningsTimestamp", "v7:earningsTimestampStart", "qs:calendarEvents.earnings.earningsDate.0"),
            requiredCluster("nextEarnings", "nextEarnings.windowStart", EPOCH_SECONDS, "v7:earningsTimestampStart"),
            requiredCluster("nextEarnings", "nextEarnings.windowEnd", EPOCH_SECONDS, "v7:earningsTimestampEnd"),
            requiredCluster("nextEarnings", "nextEarnings.isEstimate", RAW, "v7:isEarningsDateEstimate"),
            optional("bookValue", RAW, "v7:bookValue", "qs:defaultKeyStatistics.bookValue"),
            optional("priceToBook", RAW, "v7:priceToBook", "qs:defaultKeyStatistics.priceToBook"),
            optional("trailingEps", RAW, "v7:epsTrailingTwelveMonths", "qs:defaultKeyStatistics.trailingEps"),
            optional("forwardEps", RAW, "v7:epsForward", "qs:defaultKeyStatistics.forwardEps"),
            optional("forwardPE", RAW, "v7:forwardPE", "qs:summaryDetail.forwardPE", "qs:defaultKeyStatistics.forwardPE"),
            optional("trailingPE", RAW, "v7:trailingPE", "qs:summaryDetail.trailingPE"),
            clustered("trailingDividend", "trailingDividend.rate", RAW, "v7:trailingAnnualDividendRate", "qs:summaryDetail.trailingAnnualDividendRate"),
            clustered("trailingDividend", "trailingDividend.yield", RAW, "v7:trailingAnnualDividendYield", "qs:summaryDetail.trailingAnnualDividendYield"),
            clustered("currentDividend", "currentDividend.rate", RAW, "v7:dividendRate", "qs:summaryDetail.dividendRate"),
            clustered("currentDividend", "currentDividend.yield", RAW, "v7:dividendYield|PERCENT", "qs:summaryDetail.dividendYield"),
            clustered("currentYearEps", "currentYearEps.eps", RAW, "v7:epsCurrentYear"),
            clustered("currentYearEps", "currentYearEps.priceToEps", RAW, "v7:priceEpsCurrentYear"),
            optional("averageAnalystRating", RAW, "v7:averageAnalystRating"));

    public static final List<FieldSpec> SNAPSHOT = Specs.concat(CoreSpecs.CORE, SessionSpecs.SESSION, BookSpecs.BOOK, PostMarketSpecs.POST_MARKET, OWN);
}
