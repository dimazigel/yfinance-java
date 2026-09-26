package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.EtfSpecs;
import io.github.dimazigel.yfinance.assembly.specs.MutualFundSpecs;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Fund;
import io.github.dimazigel.yfinance.instrument.IntradayTraded;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FundBuildersTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Test
    void usEtfHasFundMetricsFromV7() {
        var r = Resolver.resolve(InstrumentFixtures.payload("SPY", false), EtfSpecs.SNAPSHOT);
        assertThat(r.missingRequired()).isEmpty();          // ytd/3m returns come from v7 for US ETFs
        Etf spy = EtfBuilder.build(r, NOW);
        assertThat(spy.expenseRatio()).isPresent();
        assertThat(spy.netAssets()).isPresent();
        assertThat(spy.session().volume()).isPositive();
        assertThat(spy.book()).isPresent();
        assertThat(spy.ytdReturn()).isNotNull();
        assertThat(spy).isInstanceOf(Fund.class).isInstanceOf(IntradayTraded.class);
    }

    @Test
    void ucitsEtfNeedsQuoteSummaryForReturnsAndExpenseRatioIsAFractionFromEitherSource() {
        var v7Only = Resolver.resolve(InstrumentFixtures.payload("CSPX.L", false), EtfSpecs.SNAPSHOT);
        assertThat(v7Only.missingRequired()).containsExactlyInAnyOrder("ytdReturn", "threeMonthReturn"); // fallback trigger

        var withModules = Resolver.resolve(InstrumentFixtures.payload("CSPX.L", true), EtfSpecs.SNAPSHOT);
        assertThat(withModules.missingRequired()).isEmpty();   // fundPerformance.trailingReturns fills them
        Etf cspx = EtfBuilder.build(withModules, NOW);
        assertThat(cspx.expenseRatio()).isPresent();
        assertThat(cspx.expenseRatio().get()).isEqualByComparingTo("0.0007");   // v7 netExpenseRatio 0.07 |PERCENT -> fraction
        assertThat(cspx.ytdReturn()).isNotNull();

        ObjectNode row = InstrumentFixtures.v7Row("CSPX.L").deepCopy();
        row.remove("netExpenseRatio");
        var modules = InstrumentFixtures.qsModules("CSPX.L");
        var fromQs = EtfBuilder.build(Resolver.resolve(new Payload(Symbol.of("CSPX.L"), Optional.of(row), modules), EtfSpecs.SNAPSHOT), NOW);
        assertThat(fromQs.expenseRatio()).isPresent();
        assertThat(fromQs.expenseRatio().get()).isEqualByComparingTo("0.0007");   // qs fundProfile: already a fraction, unchanged

        var withoutEither = new HashMap<>(modules);
        ObjectNode fundProfile = ((ObjectNode) modules.get("fundProfile")).deepCopy();
        ((ObjectNode) fundProfile.get("feesExpensesInvestment")).remove("annualReportExpenseRatio");
        withoutEither.put("fundProfile", fundProfile);
        var expenseless = EtfBuilder.build(Resolver.resolve(new Payload(Symbol.of("CSPX.L"), Optional.of(row), withoutEither), EtfSpecs.SNAPSHOT), NOW);
        assertThat(expenseless.expenseRatio()).isEmpty();
    }

    @Test
    void equityLikeStatsIsAnAllOrNothingCluster() {
        Etf gld = EtfBuilder.build(Resolver.resolve(InstrumentFixtures.payload("GLD", true), EtfSpecs.SNAPSHOT), NOW);
        assertThat(gld.equityLikeStats()).isPresent();
        assertThat(gld.equityLikeStats().get().bookValue()).isEqualByComparingTo("170.017");
        assertThat(gld.equityLikeStats().get().sharesOutstanding()).isEqualTo(260300000L);

        ObjectNode row = InstrumentFixtures.v7Row("GLD").deepCopy();
        row.remove("bookValue");   // 3 of 4 present
        var r = Resolver.resolve(new Payload(Symbol.of("GLD"), Optional.of(row), Map.of()), EtfSpecs.SNAPSHOT);
        assertThat(EtfBuilder.build(r, NOW).equityLikeStats()).isEmpty();
    }

    @Test
    void mutualFundHasNoSessionButGuaranteedFundMetrics() {
        var r = Resolver.resolve(InstrumentFixtures.payload("VFIAX", false), MutualFundSpecs.SNAPSHOT);
        assertThat(r.missingRequired()).isEmpty();
        MutualFund vfiax = MutualFundBuilder.build(r, NOW);
        assertThat(vfiax).isNotInstanceOf(IntradayTraded.class);
        assertThat(vfiax.expenseRatio()).isPositive();
        assertThat(vfiax.netAssets()).isPositive();
        assertThat(vfiax.yield()).isLessThan(java.math.BigDecimal.ONE);   // a fraction
        assertThat(vfiax.threeMonthReturn()).isNotNull();
    }
}
