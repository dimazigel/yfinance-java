package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.EtfSpecs;
import io.github.dimazigel.yfinance.assembly.specs.MutualFundSpecs;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Fund;
import io.github.dimazigel.yfinance.instrument.IntradayTraded;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import java.time.Instant;
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
    void ucitsEtfNeedsQuoteSummaryForReturnsAndHasNoExpenseRatioAnywhere() {
        var v7Only = Resolver.resolve(InstrumentFixtures.payload("CSPX.L", false), EtfSpecs.SNAPSHOT);
        assertThat(v7Only.missingRequired()).containsExactlyInAnyOrder("ytdReturn", "threeMonthReturn"); // fallback trigger

        var withModules = Resolver.resolve(InstrumentFixtures.payload("CSPX.L", true), EtfSpecs.SNAPSHOT);
        assertThat(withModules.missingRequired()).isEmpty();   // fundPerformance.trailingReturns fills them
        Etf cspx = EtfBuilder.build(withModules, NOW);
        assertThat(cspx.expenseRatio()).as("UCITS listings lack it in every source").isEmpty();
        assertThat(cspx.ytdReturn()).isNotNull();
    }

    @Test
    void goldEtfHasNoEquityLikeStats() {
        Etf gld = EtfBuilder.build(Resolver.resolve(InstrumentFixtures.payload("GLD", true), EtfSpecs.SNAPSHOT), NOW);
        assertThat(gld.equityLikeStats()).isEmpty();
        assertThat(gld.trailingPE()).isEmpty();
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
