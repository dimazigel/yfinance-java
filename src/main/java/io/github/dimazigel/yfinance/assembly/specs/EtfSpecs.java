package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.clustered;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.optional;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** ETF snapshot: all guaranteed and optional fields for an Etf record. */
public final class EtfSpecs {

    private EtfSpecs() {}

    private static final List<FieldSpec> OWN = List.of(
            required("ytdReturn", RAW, "v7:ytdReturn", "qs:defaultKeyStatistics.ytdReturn", "qs:fundPerformance.trailingReturns.ytd"),
            required("threeMonthReturn", RAW, "v7:trailingThreeMonthReturns", "qs:fundPerformance.trailingReturns.threeMonth"),
            optional("netAssets", RAW, "v7:netAssets", "qs:defaultKeyStatistics.totalAssets", "qs:summaryDetail.totalAssets"),
            optional("expenseRatio", RAW, "v7:netExpenseRatio|PERCENT", "qs:fundProfile.feesExpensesInvestment.annualReportExpenseRatio"),
            optional("yield", RAW, "v7:dividendYield|PERCENT", "qs:summaryDetail.yield", "qs:defaultKeyStatistics.yield"),
            optional("navPrice", RAW, "qs:summaryDetail.navPrice"),
            optional("beta3Year", RAW, "qs:defaultKeyStatistics.beta3Year"),
            optional("trailingThreeMonthNavReturns", RAW, "v7:trailingThreeMonthNavReturns"),
            optional("trailingPE", RAW, "v7:trailingPE", "qs:summaryDetail.trailingPE"),
            clustered("equityLikeStats", "equityLikeStats.bookValue", RAW, "v7:bookValue"),
            clustered("equityLikeStats", "equityLikeStats.priceToBook", RAW, "v7:priceToBook"),
            clustered("equityLikeStats", "equityLikeStats.sharesOutstanding", RAW, "v7:sharesOutstanding"),
            clustered("equityLikeStats", "equityLikeStats.financialCurrency", RAW, "v7:financialCurrency"),
            clustered("trailingDividend", "trailingDividend.rate", RAW, "v7:trailingAnnualDividendRate"),
            clustered("trailingDividend", "trailingDividend.yield", RAW, "v7:trailingAnnualDividendYield"));

    public static final List<FieldSpec> SNAPSHOT = Specs.concat(CoreSpecs.CORE, SessionSpecs.SESSION, BookSpecs.BOOK, PostMarketSpecs.POST_MARKET, OWN);
}
