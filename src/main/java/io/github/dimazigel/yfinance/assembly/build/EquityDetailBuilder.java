package io.github.dimazigel.yfinance.assembly.build;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.detail.EquityDetail;
import io.github.dimazigel.yfinance.detail.EquityDetail.AnalystView;
import io.github.dimazigel.yfinance.detail.EquityDetail.Breakdown;
import io.github.dimazigel.yfinance.detail.EquityDetail.CompanyProfile;
import io.github.dimazigel.yfinance.detail.EquityDetail.FinancialHealth;
import io.github.dimazigel.yfinance.detail.EquityDetail.FiscalCalendar;
import io.github.dimazigel.yfinance.detail.EquityDetail.Governance;
import io.github.dimazigel.yfinance.detail.EquityDetail.LastDividend;
import io.github.dimazigel.yfinance.detail.EquityDetail.LastSplit;
import io.github.dimazigel.yfinance.detail.EquityDetail.Liquidity;
import io.github.dimazigel.yfinance.detail.EquityDetail.Margins;
import io.github.dimazigel.yfinance.detail.EquityDetail.Officer;
import io.github.dimazigel.yfinance.detail.EquityDetail.Ownership;
import io.github.dimazigel.yfinance.detail.EquityDetail.Rating;
import io.github.dimazigel.yfinance.detail.EquityDetail.ShortInterest;
import io.github.dimazigel.yfinance.detail.EquityDetail.Statistics;
import io.github.dimazigel.yfinance.detail.EquityDetail.Targets;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link Resolved} → {@link EquityDetail}. Callers must have checked {@code missingRequired()} first. */
public final class EquityDetailBuilder {

    private EquityDetailBuilder() {}

    public static EquityDetail build(Resolved r, Map<String, JsonNode> modules, Symbol symbol, Instant fetchedAt) {
        return new EquityDetail(symbol, profile(r), statistics(r), financials(r), analysts(r), ownership(r, modules), fetchedAt);
    }

    // ---- profile ----

    private static CompanyProfile profile(Resolved r) {
        return new CompanyProfile(
                r.string("profile.sector"),
                r.string("profile.industry"),
                r.string("profile.country"),
                r.string("profile.city"),
                r.string("profile.address1"),
                r.string("profile.zip"),
                website(r),
                r.string("profile.longBusinessSummary"),
                officers(r),
                r.optInt("profile.fullTimeEmployees"),
                r.optString("profile.phone"),
                r.optString("profile.state"),
                r.optString("profile.irWebsite").flatMap(Nodes::uri),
                r.clusterPresent("governance") ? Optional.of(governance(r)) : Optional.empty());
    }

    /** {@code profile.website} is required but "lenient URI" per the appendix: tolerate unencoded spaces. */
    private static URI website(Resolved r) {
        String raw = r.string("profile.website");
        return Nodes.uri(raw)
                .or(() -> Nodes.uri(raw.replace(" ", "%20")))
                .orElseThrow(() -> new IllegalStateException("profile.website is not a valid URI: " + raw));
    }

    private static Governance governance(Resolved r) {
        return new Governance(
                r.intValue("profile.governance.auditRisk"),
                r.intValue("profile.governance.boardRisk"),
                r.intValue("profile.governance.compensationRisk"),
                r.intValue("profile.governance.shareholderRightsRisk"),
                r.intValue("profile.governance.overallRisk"));
    }

    private static List<Officer> officers(Resolved r) {
        var out = new ArrayList<Officer>();
        for (JsonNode node : r.list("profile.officers")) {
            Optional<String> name = Nodes.optString(node, "name");
            Optional<String> title = Nodes.optString(node, "title");
            if (name.isPresent() && title.isPresent()) {
                out.add(new Officer(name.get(), title.get(), Nodes.optInt(node, "age"), Nodes.optLong(node, "totalPay")));
            }
        }
        return List.copyOf(out);
    }

    // ---- statistics ----

    private static Statistics statistics(Resolved r) {
        return new Statistics(
                r.longValue("statistics.floatShares"),
                r.decimal("statistics.heldPercentInsiders"),
                r.decimal("statistics.heldPercentInstitutions"),
                r.decimal("statistics.profitMargins"),
                r.optDecimal("statistics.beta"),
                r.optDecimal("statistics.enterpriseValue"),
                r.optDecimal("statistics.enterpriseToRevenue"),
                r.optDecimal("statistics.enterpriseToEbitda"),
                r.clusterPresent("fiscal") ? Optional.of(fiscal(r)) : Optional.empty(),
                r.optDecimal("statistics.pegRatio"),
                r.optDecimal("statistics.payoutRatio"),
                r.optDecimal("statistics.priceToSales"),
                r.optDecimal("statistics.earningsQuarterlyGrowth"),
                r.clusterPresent("shortInterest") ? Optional.of(shortInterest(r)) : Optional.empty(),
                r.clusterPresent("lastSplit") ? Optional.of(lastSplit(r)) : Optional.empty(),
                r.clusterPresent("lastDividend") ? Optional.of(lastDividend(r)) : Optional.empty(),
                r.optDate("statistics.exDividendDate"),
                r.optDecimal("statistics.fiveYearAvgDividendYield"));
    }

    private static FiscalCalendar fiscal(Resolved r) {
        return new FiscalCalendar(
                r.date("statistics.fiscal.lastFiscalYearEnd"),
                r.date("statistics.fiscal.nextFiscalYearEnd"),
                r.date("statistics.fiscal.mostRecentQuarter"));
    }

    private static ShortInterest shortInterest(Resolved r) {
        return new ShortInterest(
                r.longValue("statistics.shortInterest.sharesShort"),
                r.decimal("statistics.shortInterest.shortRatio"),
                r.date("statistics.shortInterest.date"),
                r.longValue("statistics.shortInterest.sharesShortPriorMonth"),
                r.decimal("statistics.shortInterest.percentSharesOut"),
                r.optDecimal("statistics.shortInterest.percentOfFloat"));
    }

    private static LastSplit lastSplit(Resolved r) {
        return new LastSplit(r.date("statistics.lastSplit.date"), r.string("statistics.lastSplit.factor"));
    }

    private static LastDividend lastDividend(Resolved r) {
        return new LastDividend(r.decimal("statistics.lastDividend.value"), r.date("statistics.lastDividend.date"));
    }

    // ---- financials ----

    private static FinancialHealth financials(Resolved r) {
        return new FinancialHealth(
                r.decimal("financials.currentPrice"),
                r.decimal("financials.totalRevenue"),
                r.decimal("financials.revenuePerShare"),
                r.decimal("financials.grossProfits"),
                new Margins(
                        r.decimal("financials.margins.gross"),
                        r.decimal("financials.margins.operating"),
                        r.decimal("financials.margins.ebitda")),
                r.decimal("financials.totalCash"),
                r.decimal("financials.totalCashPerShare"),
                r.decimal("financials.totalDebt"),
                r.optDecimal("financials.revenueGrowth"),
                r.optDecimal("financials.debtToEquity"),
                r.optDecimal("financials.ebitda"),
                r.optDecimal("financials.freeCashflow"),
                r.optDecimal("financials.operatingCashflow"),
                r.optDecimal("financials.returnOnEquity"),
                r.optDecimal("financials.returnOnAssets"),
                r.clusterPresent("liquidity")
                        ? Optional.of(new Liquidity(
                                r.decimal("financials.liquidity.currentRatio"), r.decimal("financials.liquidity.quickRatio")))
                        : Optional.empty(),
                r.optDecimal("financials.earningsGrowth"));
    }

    // ---- analysts ----

    private static AnalystView analysts(Resolved r) {
        return new AnalystView(
                r.string("analysts.recommendationKey"),
                r.clusterPresent("targets") ? Optional.of(targets(r)) : Optional.empty(),
                r.clusterPresent("rating") ? Optional.of(rating(r)) : Optional.empty(),
                RowMappers.recommendationTrend(r.list("analysts.recommendationTrend")),
                RowMappers.earningsHistory(r.list("analysts.earningsHistory")),
                RowMappers.estimates(r.list("analysts.earningsEstimates"), "earningsEstimate"),
                RowMappers.estimates(r.list("analysts.revenueEstimates"), "revenueEstimate"),
                RowMappers.epsTrend(r.list("analysts.epsTrend")),
                RowMappers.epsRevisions(r.list("analysts.epsRevisions")),
                RowMappers.growth(r.list("analysts.growthEstimates")),
                RowMappers.upgradesDowngrades(r.list("analysts.upgradesDowngrades")),
                RowMappers.secFilings(r.list("analysts.secFilings")));
    }

    private static Targets targets(Resolved r) {
        return new Targets(
                r.decimal("analysts.targets.low"),
                r.decimal("analysts.targets.mean"),
                r.decimal("analysts.targets.median"),
                r.decimal("analysts.targets.high"),
                r.intValue("analysts.targets.analystCount"));
    }

    private static Rating rating(Resolved r) {
        return new Rating(r.decimal("analysts.rating.mean"));
    }

    // ---- ownership ----

    private static Ownership ownership(Resolved r, Map<String, JsonNode> modules) {
        JsonNode netShareModule = modules.get("netSharePurchaseActivity");
        if (netShareModule == null) {
            throw new IllegalStateException("netSharePurchaseActivity module missing though the field resolved");
        }
        return new Ownership(
                breakdown(r),
                RowMappers.holders(r.list("ownership.institutions")),
                RowMappers.holders(r.list("ownership.funds")),
                RowMappers.insiders(r.list("ownership.insiders")),
                RowMappers.insiderTransactions(r.list("ownership.insiderTransactions")),
                RowMappers.netSharePurchaseActivity(netShareModule));
    }

    private static Breakdown breakdown(Resolved r) {
        return new Breakdown(
                r.decimal("ownership.breakdown.insidersPercentHeld"),
                r.decimal("ownership.breakdown.institutionsPercentHeld"),
                r.decimal("ownership.breakdown.institutionsFloatPercentHeld"),
                r.intValue("ownership.breakdown.institutionsCount"));
    }
}
