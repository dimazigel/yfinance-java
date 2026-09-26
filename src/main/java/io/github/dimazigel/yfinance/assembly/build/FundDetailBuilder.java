package io.github.dimazigel.yfinance.assembly.build;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.detail.EtfDetail;
import io.github.dimazigel.yfinance.detail.FundDetail;
import io.github.dimazigel.yfinance.detail.MutualFundDetail;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.net.URI;
import java.time.Instant;
import java.util.List;

/** {@link Resolved} → {@link EtfDetail} or {@link MutualFundDetail}. Callers must have checked {@code missingRequired()} first. */
public final class FundDetailBuilder {

    private FundDetailBuilder() {}

    public static EtfDetail etf(Resolved r, Symbol symbol, Instant fetchedAt) {
        return new EtfDetail(
                symbol,
                r.string("family"),
                r.string("legalType"),
                r.date("inceptionDate"),
                trailingReturns(r),
                annualReturns(r),
                allocation(r),
                equityValuation(r),
                holdings(r),
                sectors(r),
                bondRatings(r),
                r.optString("category"),
                r.optDecimal("beta3Year"),
                r.optString("longBusinessSummary"),
                r.optString("styleBoxUrl").flatMap(Nodes::uri),
                fetchedAt);
    }

    public static MutualFundDetail mutualFund(Resolved r, Symbol symbol, Instant fetchedAt) {
        return new MutualFundDetail(
                symbol,
                r.string("family"),
                r.date("inceptionDate"),
                trailingReturns(r),
                annualReturns(r),
                allocation(r),
                equityValuation(r),
                holdings(r),
                sectors(r),
                bondRatings(r),
                r.optString("category"),
                r.string("longBusinessSummary"),
                new MutualFundDetail.Morningstar(r.intValue("morningstar.overallRating"), r.intValue("morningstar.riskRating")),
                r.decimal("annualHoldingsTurnover"),
                r.decimal("lastCapGain"),
                r.decimal("lastDividendValue"),
                r.decimal("beta3Year"),
                new MutualFundDetail.Minimums(r.decimal("minimums.initial"), r.decimal("minimums.subsequent")),
                r.list("brokerages").stream().map(JsonNode::asText).toList(),
                new MutualFundDetail.LoadAdjustedReturns(
                        r.decimal("loadAdjustedReturns.oneYear"),
                        r.decimal("loadAdjustedReturns.threeYear"),
                        r.decimal("loadAdjustedReturns.fiveYear"),
                        r.decimal("loadAdjustedReturns.tenYear")),
                new MutualFundDetail.RankInCategory(
                        r.decimal("rankInCategory.ytd"),
                        r.decimal("rankInCategory.oneMonth"),
                        r.decimal("rankInCategory.threeMonth"),
                        r.decimal("rankInCategory.oneYear"),
                        r.decimal("rankInCategory.threeYear"),
                        r.decimal("rankInCategory.fiveYear")),
                URI.create(r.string("styleBoxUrl")),
                fetchedAt);
    }

    static FundDetail.TrailingReturns trailingReturns(Resolved r) {
        return new FundDetail.TrailingReturns(
                r.decimal("trailingReturns.ytd"),
                r.decimal("trailingReturns.oneMonth"),
                r.decimal("trailingReturns.threeMonth"),
                r.decimal("trailingReturns.oneYear"),
                r.decimal("trailingReturns.threeYear"),
                r.decimal("trailingReturns.fiveYear"),
                r.decimal("trailingReturns.tenYear"),
                r.date("trailingReturns.asOf"));
    }

    static List<FundDetail.YearReturn> annualReturns(Resolved r) {
        return r.list("annualTotalReturns").stream()
                .filter(n -> Nodes.get(n, "year").isPresent() && Nodes.get(n, "annualValue").isPresent())
                .map(n -> new FundDetail.YearReturn(Integer.parseInt(Nodes.string(n, "year")), Nodes.decimal(n, "annualValue")))
                .toList();
    }

    static FundDetail.Allocation allocation(Resolved r) {
        return new FundDetail.Allocation(
                r.decimal("allocation.stock"),
                r.decimal("allocation.bond"),
                r.decimal("allocation.cash"),
                r.decimal("allocation.preferred"),
                r.decimal("allocation.convertible"),
                r.decimal("allocation.other"));
    }

    static FundDetail.EquityValuation equityValuation(Resolved r) {
        return new FundDetail.EquityValuation(
                r.decimal("equityValuation.priceToEarnings"),
                r.decimal("equityValuation.priceToBook"),
                r.decimal("equityValuation.priceToSales"),
                r.decimal("equityValuation.priceToCashflow"));
    }

    static List<FundDetail.Holding> holdings(Resolved r) {
        return r.list("holdings").stream()
                .filter(n -> Nodes.get(n, "symbol").isPresent() && Nodes.get(n, "holdingPercent").isPresent())
                .map(n -> new FundDetail.Holding(
                        Nodes.string(n, "symbol"),
                        Nodes.optString(n, "holdingName").orElse(Nodes.string(n, "symbol")),
                        Nodes.decimal(n, "holdingPercent")))
                .toList();
    }

    static List<FundDetail.SectorWeight> sectors(Resolved r) {
        return Nodes.singleKeyList(r.list("sectorWeightings")).stream()
                .map(e -> new FundDetail.SectorWeight(e.getKey(), e.getValue()))
                .toList();
    }

    static List<FundDetail.BondRating> bondRatings(Resolved r) {
        return Nodes.singleKeyList(r.list("bondRatings")).stream()
                .map(e -> new FundDetail.BondRating(e.getKey(), e.getValue()))
                .toList();
    }
}
