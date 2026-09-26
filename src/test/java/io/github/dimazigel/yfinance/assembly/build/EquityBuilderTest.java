package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.EquitySpecs;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class EquityBuilderTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Test
    void appleHasEveryGuaranteeAndMostOptionals() {
        var r = Resolver.resolve(InstrumentFixtures.payload("AAPL", false), EquitySpecs.SNAPSHOT);
        assertThat(r.missingRequired()).isEmpty();

        Equity aapl = EquityBuilder.build(r, NOW);
        assertThat(aapl.assetClass()).isEqualTo(AssetClass.EQUITY);
        assertThat(aapl.valuation().marketCap()).isGreaterThan(new BigDecimal("1000000000000"));
        assertThat(aapl.valuation().sharesOutstanding()).isPositive();
        assertThat(aapl.valuation().financialCurrency().code()).isEqualTo("USD");
        assertThat(aapl.nextEarnings().windowStart()).isBeforeOrEqualTo(aapl.nextEarnings().windowEnd());
        assertThat(aapl.session().volume()).isPositive();
        assertThat(aapl.book()).isPresent();
        assertThat(aapl.bookValue()).isPresent();
        assertThat(aapl.trailingEps()).isPresent();
        assertThat(aapl.trailingPE()).isPresent();
        assertThat(aapl.averageAnalystRating()).isPresent();
        assertThat(aapl.currentYearEps()).isPresent();
        assertThat(aapl.fetchedAt()).isEqualTo(NOW);
    }

    @Test
    void lossMakingEquityHasNoTrailingPeOrDividend() {
        var r = Resolver.resolve(InstrumentFixtures.payload("PLUG", false), EquitySpecs.SNAPSHOT);
        assertThat(r.missingRequired()).isEmpty();
        Equity plug = EquityBuilder.build(r, NOW);
        assertThat(plug.trailingPE()).isEmpty();
        assertThat(plug.currentDividend()).isEmpty();
        assertThat(plug.valuation().marketCap()).isPositive();   // still a full equity
    }

    @Test
    void samsungLacksBookValueButIsStillAnEquity() {
        var r = Resolver.resolve(InstrumentFixtures.payload("005930.KS", true), EquitySpecs.SNAPSHOT);
        assertThat(r.missingRequired()).as("Intrinsic rule: nothing Samsung lacks is required").isEmpty();
        Equity samsung = EquityBuilder.build(r, NOW);
        assertThat(samsung.bookValue()).isEmpty();
        assertThat(samsung.trailingEps()).isEmpty();
        assertThat(samsung.valuation().marketCap()).isPositive();
    }

    @Test
    void preferredShareFailsTheMarketCapGuarantee() {
        var r = Resolver.resolve(InstrumentFixtures.payload("BAC-PL", true), EquitySpecs.SNAPSHOT);
        assertThat(r.missingRequired()).contains("marketCap");   // -> Unclassified in InstrumentService
    }

    @Test
    void currentDividendYieldIsAFractionFromEitherSource() {   // Review Focus 4
        ObjectNode row = (ObjectNode) InstrumentFixtures.v7Row("AAPL").deepCopy();
        row.put("dividendRate", 1.08).put("dividendYield", 0.32);            // v7: percent
        var fromV7 = EquityBuilder.build(Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.of(row), Map.of()), EquitySpecs.SNAPSHOT), NOW);
        assertThat(fromV7.currentDividend()).isPresent();
        assertThat(fromV7.currentDividend().get().yield()).isEqualByComparingTo("0.0032");

        row.remove("dividendYield");
        row.remove("dividendRate");
        var modules = new java.util.HashMap<>(InstrumentFixtures.qsModules("AAPL"));
        ObjectNode sd = (ObjectNode) modules.get("summaryDetail").deepCopy();
        sd.put("dividendRate", 1.08).put("dividendYield", 0.0032);          // summaryDetail: fraction
        modules.put("summaryDetail", sd);
        var fromQs = EquityBuilder.build(Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.of(row), modules), EquitySpecs.SNAPSHOT), NOW);
        assertThat(fromQs.currentDividend().get().yield()).isEqualByComparingTo("0.0032");
    }

    @Test
    void changePercentIsAFractionFromEitherSource() {   // final review, finding 1: v7 percent, price module fraction
        ObjectNode row = (ObjectNode) InstrumentFixtures.v7Row("AAPL").deepCopy();
        assertThat(row.get("regularMarketChangePercent").decimalValue()).isEqualByComparingTo("1.5331");
        var fromV7 = EquityBuilder.build(Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.of(row), Map.of()), EquitySpecs.SNAPSHOT), NOW);
        assertThat(fromV7.core().changePercent()).isEqualByComparingTo("0.015331");

        row.remove("regularMarketChangePercent");
        var modules = InstrumentFixtures.qsModules("AAPL");
        assertThat(modules.get("price").get("regularMarketChangePercent").decimalValue()).isEqualByComparingTo("0.015331");
        var fromQs = EquityBuilder.build(Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.of(row), modules), EquitySpecs.SNAPSHOT), NOW);
        assertThat(fromQs.core().changePercent()).as("the price module already serves a fraction").isEqualByComparingTo("0.015331");
    }

    @Test
    void postMarketChangePercentIsAFractionFromEitherSource() {   // final review, finding 1
        ObjectNode row = (ObjectNode) InstrumentFixtures.v7Row("AAPL").deepCopy();
        assertThat(row.get("postMarketChangePercent").decimalValue()).isEqualByComparingTo("0.11443085");
        var fromV7 = EquityBuilder.build(Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.of(row), Map.of()), EquitySpecs.SNAPSHOT), NOW);
        assertThat(fromV7.postMarket()).isPresent();
        assertThat(fromV7.postMarket().get().changePercent()).isEqualByComparingTo("0.0011443085");

        row.remove("postMarketChangePercent");
        var modules = InstrumentFixtures.qsModules("AAPL");
        assertThat(modules.get("price").get("postMarketChangePercent").decimalValue()).isEqualByComparingTo("0.0011443085");
        var fromQs = EquityBuilder.build(Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.of(row), modules), EquitySpecs.SNAPSHOT), NOW);
        assertThat(fromQs.postMarket()).as("the cluster completes through the price module").isPresent();
        assertThat(fromQs.postMarket().get().changePercent()).isEqualByComparingTo("0.0011443085");
    }

    @Test
    void postMarketIsAllOrNothing() {
        ObjectNode row = (ObjectNode) InstrumentFixtures.v7Row("AAPL").deepCopy();
        row.remove("postMarketTime");   // three of four present
        var r = Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.of(row), Map.of()), EquitySpecs.SNAPSHOT);
        assertThat(EquityBuilder.build(r, NOW).postMarket()).isEmpty();
    }
}
