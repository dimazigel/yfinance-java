package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.EquityDetailSpecs;
import io.github.dimazigel.yfinance.detail.EquityDetail;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class EquityDetailBuilderTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    private static EquityDetail build(String symbol) {
        Map<String, JsonNode> modules = InstrumentFixtures.qsModules(symbol);
        var payload = new Payload(Symbol.of(symbol), Optional.of(InstrumentFixtures.v7Row(symbol)), modules);
        var r = Resolver.resolve(payload, EquityDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).as(symbol + " missing").isEmpty();
        return EquityDetailBuilder.build(r, modules, Symbol.of(symbol), NOW);
    }

    @Test
    void appleDetailIsComplete() {
        EquityDetail d = build("AAPL");
        assertThat(d.profile().sector()).isEqualTo("Technology");
        assertThat(d.profile().officers()).isNotEmpty().allSatisfy(o -> assertThat(o.name()).isNotBlank());
        assertThat(d.profile().governance()).isPresent();
        assertThat(d.statistics().floatShares()).isPositive();
        assertThat(d.statistics().heldPercentInstitutions()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(d.statistics().beta()).isPresent();
        assertThat(d.statistics().fiscal()).isPresent();
        assertThat(d.statistics().shortInterest()).isPresent();
        assertThat(d.financials().totalRevenue()).isPositive();
        assertThat(d.financials().margins().gross()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(d.financials().liquidity()).isPresent();
        assertThat(d.analysts().recommendationKey()).isNotBlank();
        assertThat(d.analysts().targets()).isPresent();
        assertThat(d.analysts().targets().get().low()).isLessThanOrEqualTo(d.analysts().targets().get().high());
        assertThat(d.analysts().rating()).isPresent();
        assertThat(d.analysts().rating().get().mean()).isBetween(BigDecimal.ONE, BigDecimal.valueOf(5));
        assertThat(d.analysts().recommendationTrend()).isNotEmpty();
        assertThat(d.analysts().earningsEstimates()).isNotEmpty();
        assertThat(d.analysts().epsTrend()).isNotEmpty();
        assertThat(d.ownership().breakdown().institutionsCount()).isPositive();
        assertThat(d.ownership().institutions()).isNotEmpty().allSatisfy(h -> assertThat(h.organization()).isNotBlank());
        assertThat(d.ownership().insiders()).isNotEmpty();
        assertThat(d.ownership().netSharePurchaseActivity().period()).isNotBlank();
    }

    @Test
    void lossMakingSmallCapHasEmptyOptionalsNotFailures() {
        EquityDetail plug = build("PLUG");
        assertThat(plug.statistics().pegRatio().get()).isEqualByComparingTo("0.84"); // present; drifted from the 84% survey figure
        assertThat(plug.statistics().fiveYearAvgDividendYield()).isEmpty();
        assertThat(plug.statistics().exDividendDate()).isEmpty();
        assertThat(plug.statistics().lastDividend()).isEmpty();
        assertThat(plug.financials().earningsGrowth()).isEmpty();
        assertThat(plug.financials().debtToEquity().get()).isEqualByComparingTo("1.76565"); // wire 176.565, PERCENT -> fraction
        assertThat(plug.analysts().upgradesDowngrades()).isNotNull();    // possibly empty list, never absent
        assertThat(plug.analysts().secFilings()).isNotNull();
    }

    @Test
    void missingRequiredModuleIsReportedByFieldName() {
        var modules = new HashMap<>(InstrumentFixtures.qsModules("AAPL"));
        modules.remove("financialData");
        var r = Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.empty(), modules), EquityDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).contains("financials.totalRevenue", "analysts.recommendationKey");
    }

    @Test
    void recommendationPeriodsMissingACountAreDroppedNotZeroed() {   // final review, finding 8
        var modules = new HashMap<>(InstrumentFixtures.qsModules("AAPL"));
        ObjectNode trend = (ObjectNode) modules.get("recommendationTrend").deepCopy();
        ((ObjectNode) trend.get("trend").get(0)).remove("buy");
        modules.put("recommendationTrend", trend);
        var r = Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.empty(), modules), EquityDetailSpecs.DETAIL);

        try (var log = LogCapture.of(RowMappers.class)) {
            EquityDetail d = EquityDetailBuilder.build(r, modules, Symbol.of("AAPL"), NOW);

            assertThat(d.analysts().recommendationTrend()).hasSize(trend.get("trend").size() - 1);
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m).startsWith("Dropped 1 of").contains("recommendation periods"));
        }
    }

    @Test
    void officersMissingATitleAreDroppedWithADebugLine() {   // final review, finding 10
        var modules = new HashMap<>(InstrumentFixtures.qsModules("AAPL"));
        ObjectNode profile = (ObjectNode) modules.get("assetProfile").deepCopy();
        ((ObjectNode) profile.get("companyOfficers").get(0)).remove("title");
        modules.put("assetProfile", profile);
        var r = Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.empty(), modules), EquityDetailSpecs.DETAIL);

        try (var log = LogCapture.of(RowMappers.class)) {
            EquityDetail d = EquityDetailBuilder.build(r, modules, Symbol.of("AAPL"), NOW);

            assertThat(d.profile().officers()).hasSize(profile.get("companyOfficers").size() - 1);
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m).startsWith("Dropped 1 of").contains("company officers"));
        }
    }

    @Test
    void rowsMissingTheirIdentifierAreDropped() throws Exception {
        var modules = new HashMap<>(InstrumentFixtures.qsModules("AAPL"));
        var trend = (ObjectNode) modules.get("recommendationTrend").deepCopy();
        ((ObjectNode) trend.get("trend").get(0)).remove("period");
        modules.put("recommendationTrend", trend);
        var r = Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.empty(), modules), EquityDetailSpecs.DETAIL);
        EquityDetail d = EquityDetailBuilder.build(r, modules, Symbol.of("AAPL"), NOW);
        assertThat(d.analysts().recommendationTrend()).hasSize(trend.get("trend").size() - 1);
    }
}
