package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.DetailSpecs;
import io.github.dimazigel.yfinance.assembly.specs.EtfDetailSpecs;
import io.github.dimazigel.yfinance.assembly.specs.MutualFundDetailSpecs;
import io.github.dimazigel.yfinance.detail.EtfDetail;
import io.github.dimazigel.yfinance.detail.MutualFundDetail;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class FundDetailBuilderTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    private static Payload detailPayload(String symbol) {
        return new Payload(Symbol.of(symbol), Optional.empty(), InstrumentFixtures.qsModules(symbol));
    }

    @Test
    void spyDetailHasEveryGuaranteeAndHoldings() {
        var r = Resolver.resolve(detailPayload("SPY"), EtfDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).isEmpty();
        EtfDetail spy = FundDetailBuilder.etf(r, Symbol.of("SPY"), NOW);
        assertThat(spy.family()).isNotBlank();
        assertThat(spy.legalType()).isNotBlank();
        assertThat(spy.inceptionDate()).isBefore(java.time.LocalDate.of(2000, 1, 1));
        assertThat(spy.trailingReturns().oneYear()).isNotNull();
        assertThat(spy.trailingReturns().asOf()).isAfter(java.time.LocalDate.of(2025, 1, 1));
        assertThat(spy.annualTotalReturns()).isNotEmpty().allSatisfy(y -> assertThat(y.year()).isBetween(1990, 2030));
        BigDecimal total = spy.allocation().stock().add(spy.allocation().bond()).add(spy.allocation().cash());
        assertThat(total).isBetween(new BigDecimal("0.9"), new BigDecimal("1.1"));   // fractions summing to ~1
        assertThat(spy.holdings()).isNotEmpty().allSatisfy(h -> {
            assertThat(h.symbol()).isNotBlank();
            assertThat(h.weight()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        });
        assertThat(spy.sectorWeightings()).isNotEmpty();
        assertThat(spy.equityValuation().priceToEarnings()).isPositive();
    }

    @Test
    void goldEtfHasNoHoldingsButIsStillComplete() {
        var r = Resolver.resolve(detailPayload("GLD"), EtfDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).isEmpty();
        EtfDetail gld = FundDetailBuilder.etf(r, Symbol.of("GLD"), NOW);
        assertThat(gld.holdings()).isEmpty();
        assertThat(gld.sectorWeightings()).isEmpty();
    }

    @Test
    void mutualFundDetailHasMorningstarAndMinimums() {
        var r = Resolver.resolve(detailPayload("VFIAX"), MutualFundDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).isEmpty();
        MutualFundDetail v = FundDetailBuilder.mutualFund(r, Symbol.of("VFIAX"), NOW);
        assertThat(v.morningstar().overallRating()).isBetween(1, 5);
        assertThat(v.minimums().initial()).isPositive();
        assertThat(v.brokerages()).isNotEmpty();
        assertThat(v.loadAdjustedReturns().oneYear()).isNotNull();
        assertThat(v.rankInCategory().ytd()).isNotNull();
        assertThat(v.styleBoxUrl().getHost()).isNotBlank();
    }

    @Test
    void holdingsWithoutANameAreDroppedNotNamedAfterTheSymbol() {   // final review, finding 9
        var modules = new HashMap<>(InstrumentFixtures.qsModules("SPY"));
        ObjectNode top = (ObjectNode) modules.get("topHoldings").deepCopy();
        ((ObjectNode) top.get("holdings").get(0)).remove("holdingName");
        modules.put("topHoldings", top);
        var r = Resolver.resolve(new Payload(Symbol.of("SPY"), Optional.empty(), modules), EtfDetailSpecs.DETAIL);

        try (var log = LogCapture.of(RowMappers.class)) {
            EtfDetail spy = FundDetailBuilder.etf(r, Symbol.of("SPY"), NOW);

            assertThat(spy.holdings()).hasSize(top.get("holdings").size() - 1)
                    .allSatisfy(h -> assertThat(h.name()).isNotEqualTo(h.symbol()));
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m).startsWith("Dropped 1 of").contains("holdings"));
        }
    }

    @Test
    void annualReturnsMissingAValueAreDroppedWithADebugLine() {   // final review, finding 10; VFIAX carries one such row
        var r = Resolver.resolve(detailPayload("VFIAX"), MutualFundDetailSpecs.DETAIL);
        int rows = InstrumentFixtures.qsModules("VFIAX").get("fundPerformance").path("annualTotalReturns").path("returns").size();

        try (var log = LogCapture.of(RowMappers.class)) {
            MutualFundDetail v = FundDetailBuilder.mutualFund(r, Symbol.of("VFIAX"), NOW);

            assertThat(v.annualTotalReturns()).hasSize(rows - 1);
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m).startsWith("Dropped 1 of").contains("annual returns"));
        }
    }

    @Test
    void moduleSetsPerClass() {
        assertThat(DetailSpecs.modules(AssetClass.ETF)).contains("fundProfile", "topHoldings", "fundPerformance");
        assertThat(DetailSpecs.modules(AssetClass.EQUITY)).contains("financialData", "earningsTrend", "insiderTransactions");
        assertThat(DetailSpecs.modules(AssetClass.INDEX)).isEmpty();
        assertThat(DetailSpecs.modules(AssetClass.FX)).isEmpty();
        assertThat(DetailSpecs.modules(AssetClass.FUTURE)).isEmpty();
    }
}
