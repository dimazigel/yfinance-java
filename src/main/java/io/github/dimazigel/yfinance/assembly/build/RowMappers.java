package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.detail.EquityDetail.NetSharePurchaseActivity;
import io.github.dimazigel.yfinance.detail.rows.EarningsHistoryEntry;
import io.github.dimazigel.yfinance.detail.rows.EpsRevisionsPeriod;
import io.github.dimazigel.yfinance.detail.rows.EpsTrendPeriod;
import io.github.dimazigel.yfinance.detail.rows.GrowthEstimate;
import io.github.dimazigel.yfinance.detail.rows.InsiderHolder;
import io.github.dimazigel.yfinance.detail.rows.InsiderTransaction;
import io.github.dimazigel.yfinance.detail.rows.InstitutionalHolder;
import io.github.dimazigel.yfinance.detail.rows.PeriodEstimate;
import io.github.dimazigel.yfinance.detail.rows.RecommendationPeriod;
import io.github.dimazigel.yfinance.detail.rows.SecFiling;
import io.github.dimazigel.yfinance.detail.rows.UpgradeDowngrade;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Maps raw JSON rows from the equity detail modules into {@code detail.rows} records, dropping
 * any row that lacks the field that identifies it or a count the record cannot do without (a
 * missing count is never coerced to 0). {@link #mapRows} is the shared drop-and-log skeleton the
 * other builders use for their own row lists.
 */
final class RowMappers {

    private static final Logger LOG = LoggerFactory.getLogger(RowMappers.class);

    private RowMappers() {}

    /** A period needs all five counts; a row missing any of them is dropped rather than read as 0. */
    static List<RecommendationPeriod> recommendationTrend(List<JsonNode> rows) {
        return mapRows(rows, "recommendation periods", row -> {
            Optional<String> period = Nodes.optString(row, "period");
            Optional<Integer> strongBuy = Nodes.optInt(row, "strongBuy");
            Optional<Integer> buy = Nodes.optInt(row, "buy");
            Optional<Integer> hold = Nodes.optInt(row, "hold");
            Optional<Integer> sell = Nodes.optInt(row, "sell");
            Optional<Integer> strongSell = Nodes.optInt(row, "strongSell");
            if (period.isEmpty() || strongBuy.isEmpty() || buy.isEmpty() || hold.isEmpty() || sell.isEmpty() || strongSell.isEmpty()) {
                return null;
            }
            return new RecommendationPeriod(period.get(), strongBuy.get(), buy.get(), hold.get(), sell.get(), strongSell.get());
        });
    }

    static List<EarningsHistoryEntry> earningsHistory(List<JsonNode> rows) {
        return mapRows(rows, "earnings history entries", row -> Nodes.optString(row, "period")
                .map(period -> new EarningsHistoryEntry(
                        period,
                        Nodes.optInstantSeconds(row, "quarter"),
                        Nodes.optDecimal(row, "epsActual"),
                        Nodes.optDecimal(row, "epsEstimate"),
                        Nodes.optDecimal(row, "epsDifference"),
                        Nodes.optDecimal(row, "surprisePercent")))
                .orElse(null));
    }

    /** Earnings or revenue estimates from {@code earningsTrend.trend}; {@code key} is {@code earningsEstimate} or {@code revenueEstimate}. */
    static List<PeriodEstimate> estimates(List<JsonNode> rows, String key) {
        return mapRows(rows, "period estimates", row -> Nodes.optString(row, "period")
                .map(period -> {
                    JsonNode estimate = row.path(key);
                    return new PeriodEstimate(
                            period,
                            Nodes.optIsoDate(row, "endDate"),
                            Nodes.optDecimal(estimate, "avg"),
                            Nodes.optDecimal(estimate, "low"),
                            Nodes.optDecimal(estimate, "high"),
                            Nodes.optInt(estimate, "numberOfAnalysts"),
                            Nodes.optDecimal(estimate, "yearAgoEps").or(() -> Nodes.optDecimal(estimate, "yearAgoRevenue")));
                })
                .orElse(null));
    }

    static List<EpsTrendPeriod> epsTrend(List<JsonNode> rows) {
        return mapRows(rows, "eps trend periods", row -> Nodes.optString(row, "period")
                .map(period -> {
                    JsonNode trend = row.path("epsTrend");
                    return new EpsTrendPeriod(
                            period,
                            Nodes.optIsoDate(row, "endDate"),
                            Nodes.optDecimal(trend, "current"),
                            Nodes.optDecimal(trend, "7daysAgo"),
                            Nodes.optDecimal(trend, "30daysAgo"),
                            Nodes.optDecimal(trend, "60daysAgo"),
                            Nodes.optDecimal(trend, "90daysAgo"));
                })
                .orElse(null));
    }

    static List<EpsRevisionsPeriod> epsRevisions(List<JsonNode> rows) {
        return mapRows(rows, "eps revisions periods", row -> Nodes.optString(row, "period")
                .map(period -> {
                    JsonNode revisions = row.path("epsRevisions");
                    return new EpsRevisionsPeriod(
                            period,
                            Nodes.optIsoDate(row, "endDate"),
                            Nodes.optInt(revisions, "upLast7days"),
                            Nodes.optInt(revisions, "upLast30days"),
                            Nodes.optInt(revisions, "downLast30days"),
                            Nodes.optInt(revisions, "downLast90days"));
                })
                .orElse(null));
    }

    static List<GrowthEstimate> growth(List<JsonNode> rows) {
        return mapRows(rows, "growth estimates", row -> Nodes.optString(row, "period")
                .map(period -> new GrowthEstimate(period, Nodes.optIsoDate(row, "endDate"), Nodes.optDecimal(row, "growth")))
                .orElse(null));
    }

    static List<UpgradeDowngrade> upgradesDowngrades(List<JsonNode> rows) {
        return mapRows(rows, "upgrade/downgrade entries", row -> Nodes.optString(row, "firm")
                .map(firm -> new UpgradeDowngrade(
                        Nodes.optInstantSeconds(row, "epochGradeDate"),
                        firm,
                        Nodes.optString(row, "toGrade"),
                        Nodes.optString(row, "fromGrade"),
                        Nodes.optString(row, "action")))
                .orElse(null));
    }

    static List<SecFiling> secFilings(List<JsonNode> rows) {
        return mapRows(rows, "SEC filings", row -> Nodes.optString(row, "title")
                .map(title -> new SecFiling(
                        Nodes.optInstantSeconds(row, "epochDate"), Nodes.optString(row, "type"), title, Nodes.optUri(row, "edgarUrl")))
                .orElse(null));
    }

    /** Institutional or fund holders from {@code institutionOwnership}/{@code fundOwnership}.ownershipList. */
    static List<InstitutionalHolder> holders(List<JsonNode> rows) {
        return mapRows(rows, "institutional holders", row -> Nodes.optString(row, "organization")
                .map(organization -> new InstitutionalHolder(
                        Nodes.optDateSeconds(row, "reportDate"),
                        organization,
                        Nodes.optDecimal(row, "pctHeld"),
                        Nodes.optLong(row, "position"),
                        Nodes.optDecimal(row, "value"),
                        Nodes.optDecimal(row, "pctChange")))
                .orElse(null));
    }

    static List<InsiderHolder> insiders(List<JsonNode> rows) {
        return mapRows(rows, "insider holders", row -> Nodes.optString(row, "name")
                .map(name -> new InsiderHolder(
                        name,
                        Nodes.optString(row, "relation"),
                        Nodes.optString(row, "transactionDescription"),
                        Nodes.optInstantSeconds(row, "latestTransDate"),
                        Nodes.optLong(row, "positionDirect"),
                        Nodes.optInstantSeconds(row, "positionDirectDate")))
                .orElse(null));
    }

    static List<InsiderTransaction> insiderTransactions(List<JsonNode> rows) {
        return mapRows(rows, "insider transactions", row -> Nodes.optString(row, "filerName")
                .map(filerName -> new InsiderTransaction(
                        Nodes.optInstantSeconds(row, "startDate"),
                        filerName,
                        Nodes.optString(row, "filerRelation"),
                        Nodes.optString(row, "transactionText"),
                        Nodes.optLong(row, "shares"),
                        Nodes.optLong(row, "value")))
                .orElse(null));
    }

    /** The single {@code netSharePurchaseActivity} module object; the caller has checked its {@code period} resolved. */
    static NetSharePurchaseActivity netSharePurchaseActivity(JsonNode module) {
        return new NetSharePurchaseActivity(
                Nodes.string(module, "period"),
                Nodes.optInt(module, "buyInfoCount"),
                Nodes.optLong(module, "buyInfoShares"),
                Nodes.optInt(module, "sellInfoCount"),
                Nodes.optLong(module, "sellInfoShares"),
                Nodes.optInt(module, "netInfoCount"),
                Nodes.optLong(module, "netInfoShares"),
                Nodes.optLong(module, "totalInsiderShares"));
    }

    /**
     * Maps each row, dropping (and logging at DEBUG, once per list) any for which {@code fn} yields
     * {@code null} — the repo's rule that every lenient skip says what it dropped and why.
     */
    static <T> List<T> mapRows(List<JsonNode> rows, String what, Function<JsonNode, @Nullable T> fn) {
        var out = new ArrayList<T>(rows.size());
        int dropped = 0;
        for (JsonNode row : rows) {
            T mapped = fn.apply(row);
            if (mapped == null) {
                dropped++;
                continue;
            }
            out.add(mapped);
        }
        if (dropped > 0) {
            LOG.atDebug().addKeyValue("dropped", dropped).addKeyValue("total", rows.size())
                    .log("Dropped {} of {} {} lacking a required field", dropped, rows.size(), what);
        }
        return List.copyOf(out);
    }
}
