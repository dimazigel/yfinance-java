package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.clustered;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.optional;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Mutual fund snapshot: all guaranteed and optional fields for a MutualFund record. No Session, no Book. */
public final class MutualFundSpecs {

    private MutualFundSpecs() {}

    private static final List<FieldSpec> OWN = List.of(
            required("netAssets", RAW, "v7:netAssets", "qs:defaultKeyStatistics.totalAssets", "qs:summaryDetail.totalAssets"),
            required("expenseRatio", RAW, "v7:netExpenseRatio|PERCENT", "qs:fundProfile.feesExpensesInvestment.annualReportExpenseRatio", "qs:defaultKeyStatistics.annualReportExpenseRatio"),
            required("yield", RAW, "v7:dividendYield|PERCENT", "qs:summaryDetail.yield"),
            required("dividendRate", RAW, "v7:dividendRate"),
            required("ytdReturn", RAW, "v7:ytdReturn|PERCENT", "qs:summaryDetail.ytdReturn", "qs:fundPerformance.trailingReturns.ytd"),
            required("threeMonthReturn", RAW, "v7:trailingThreeMonthReturns|PERCENT", "qs:fundPerformance.trailingReturns.threeMonth"),
            clustered("equityLikeStats", "equityLikeStats.bookValue", RAW, "v7:bookValue"),
            clustered("equityLikeStats", "equityLikeStats.priceToBook", RAW, "v7:priceToBook"),
            clustered("equityLikeStats", "equityLikeStats.sharesOutstanding", RAW, "v7:sharesOutstanding"),
            clustered("equityLikeStats", "equityLikeStats.financialCurrency", RAW, "v7:financialCurrency"),
            optional("trailingPE", RAW, "v7:trailingPE"),
            clustered("trailingDividend", "trailingDividend.rate", RAW, "v7:trailingAnnualDividendRate"),
            clustered("trailingDividend", "trailingDividend.yield", RAW, "v7:trailingAnnualDividendYield"));

    public static final List<FieldSpec> SNAPSHOT = Specs.concat(CoreSpecs.CORE, OWN);
}
