package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.SnapshotSpecs;
import io.github.dimazigel.yfinance.instrument.*;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OtherClassBuildersTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Test
    void indexAndFxHaveSessionAndBookOnly() {
        var idx = Resolver.resolve(InstrumentFixtures.payload("^GSPC", false), SnapshotSpecs.forClass(AssetClass.INDEX));
        assertThat(idx.missingRequired()).isEmpty();
        Index gspc = SimpleBuilders.index(idx, NOW);
        assertThat(gspc.book()).isPresent();
        assertThat(gspc.session().volume()).isPositive();

        var fx = Resolver.resolve(InstrumentFixtures.payload("EURUSD=X", false), SnapshotSpecs.forClass(AssetClass.FX));
        assertThat(fx.missingRequired()).isEmpty();
        FxPair eurusd = SimpleBuilders.fxPair(fx, NOW);
        assertThat(eurusd.core().currency().code()).isEqualTo("USD");
    }

    @Test
    void cryptoHasSupplyAndNoBook() {
        var r = Resolver.resolve(InstrumentFixtures.payload("BTC-USD", false), SnapshotSpecs.forClass(AssetClass.CRYPTO));
        assertThat(r.missingRequired()).isEmpty();
        Crypto btc = CryptoBuilder.build(r, NOW);
        assertThat(btc).isNotInstanceOf(Quoted.class);
        assertThat(btc.supply().circulating()).isPositive();
        assertThat(btc.supply().max()).isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO); // 0 for uncapped coins, kept raw
        assertThat(btc.fromCurrency()).isEqualTo("BTC");
        assertThat(btc.toCurrency().code()).isEqualTo("USD");
        assertThat(btc.toCurrency().iso()).isPresent();
        assertThat(btc.branding().coinMarketCap().getHost()).contains("coinmarketcap");
    }

    @Test
    void futureHasContractAndNoLongName() {
        var r = Resolver.resolve(InstrumentFixtures.payload("ES=F", false), SnapshotSpecs.forClass(AssetClass.FUTURE));
        assertThat(r.missingRequired()).isEmpty();
        Future es = FutureBuilder.build(r, NOW);
        assertThat(es.core().longName()).isEmpty();
        assertThat(es.contract().expireDate()).isEqualTo(java.time.LocalDate.of(2026, 12, 18));
        assertThat(es.contract().underlyingSymbol()).isEqualTo(Symbol.of("ESZ26.CME"));
        assertThat(es.contract().headSymbol()).isEqualTo(Symbol.of("ES=F"));
        assertThat(es.contract().underlyingExchangeSymbol()).isEqualTo("ESZ26.CME");
        assertThat(es.contract().openInterest()).isEqualTo(1898072L);
        assertThat(es.book()).isPresent();
    }

    @Test
    void snapshotSpecsCoverEveryClass() {
        for (AssetClass c : AssetClass.values()) {
            assertThat(SnapshotSpecs.forClass(c)).as(c.name()).isNotEmpty();
        }
        assertThat(SnapshotSpecs.forClass(AssetClass.MUTUAL_FUND)).extracting(s -> s.name()).doesNotContain("open");
        assertThat(SnapshotSpecs.BASE_FALLBACK_MODULES).containsExactly("price", "summaryDetail", "quoteType");
    }

    @Test
    void fallbackModulesReachEveryModuleAClasssFieldsReference() {
        // ETF/MutualFund ytdReturn and threeMonthReturn live in fundPerformance, not the base three
        // modules; a fallback that stopped at the base would always downgrade a symbol that needed
        // them, even after "trying" the fallback (this was a real bug: see the 282-symbol live
        // survey, where every UCITS ETF downgraded on exactly these two fields).
        assertThat(SnapshotSpecs.fallbackModules(AssetClass.ETF)).contains("fundPerformance", "defaultKeyStatistics", "fundProfile")
                .startsWith("price", "summaryDetail", "quoteType");
        assertThat(SnapshotSpecs.fallbackModules(AssetClass.MUTUAL_FUND)).contains("fundPerformance", "defaultKeyStatistics", "fundProfile");
        assertThat(SnapshotSpecs.fallbackModules(AssetClass.EQUITY)).contains("financialData", "calendarEvents", "defaultKeyStatistics");
        // Classes whose own fields never leave the base three modules get exactly the base list.
        assertThat(SnapshotSpecs.fallbackModules(AssetClass.INDEX)).isEqualTo(SnapshotSpecs.BASE_FALLBACK_MODULES);
        assertThat(SnapshotSpecs.fallbackModules(AssetClass.FX)).isEqualTo(SnapshotSpecs.BASE_FALLBACK_MODULES);
        assertThat(SnapshotSpecs.fallbackModules(AssetClass.CRYPTO)).isEqualTo(SnapshotSpecs.BASE_FALLBACK_MODULES);
        assertThat(SnapshotSpecs.fallbackModules(AssetClass.FUTURE)).isEqualTo(SnapshotSpecs.BASE_FALLBACK_MODULES);
        assertThat(SnapshotSpecs.fallbackModules(AssetClass.UNCLASSIFIED)).isEqualTo(SnapshotSpecs.BASE_FALLBACK_MODULES);
    }
}
