package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.CoreSpecs;
import io.github.dimazigel.yfinance.http.YahooObjectMapper;
import io.github.dimazigel.yfinance.instrument.MarketState;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoreBuilderTest {

    @Test
    void buildsTheUniversalCoreFromAnEquityRow() {
        var resolved = Resolver.resolve(InstrumentFixtures.payload("AAPL", false), CoreSpecs.CORE);
        assertThat(resolved.missingRequired()).isEmpty();

        var core = CoreBuilder.build(resolved);
        assertThat(core.symbol()).isEqualTo(Symbol.of("AAPL"));
        assertThat(core.shortName()).isEqualTo("Apple Inc.");
        assertThat(core.longName()).contains("Apple Inc.");
        assertThat(core.currency().iso()).contains(java.util.Currency.getInstance("USD"));
        assertThat(core.exchange()).isEqualTo("NMS");
        assertThat(core.exchangeTimezone()).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(core.marketState()).isNotEqualTo(MarketState.OTHER);
        assertThat(core.price()).isPositive();
        assertThat(core.changePercent().abs()).isLessThan(java.math.BigDecimal.ONE); // a fraction, not a percent
        assertThat(core.priceTime()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(core.firstTradeDate()).isEqualTo(java.time.Instant.ofEpochMilli(345479400000L));
        assertThat(core.averageVolume3Month()).isPositive();
        assertThat(core.hasPrePostMarketData()).isTrue();
    }

    @Test
    void coreIsCompleteForEveryClassAndLongNameIsEmptyForFutures() {
        for (String symbol : new String[] {"SPY", "VFIAX", "^GSPC", "BTC-USD", "EURUSD=X", "ES=F"}) {
            var resolved = Resolver.resolve(InstrumentFixtures.payload(symbol, false), CoreSpecs.CORE);
            assertThat(resolved.missingRequired()).as(symbol).isEmpty();
            var core = CoreBuilder.build(resolved);
            assertThat(core.symbol().value()).isEqualTo(symbol.toUpperCase(java.util.Locale.ROOT));
            if (symbol.equals("ES=F")) {
                assertThat(core.longName()).isEmpty();
            }
        }
    }

    @Test
    void coreFallsBackToQuoteSummaryModules() throws Exception {
        // No v7 row at all: price/quoteType/summaryDetail must be enough for the core (except firstTradeDate, v7-only)
        var modules = InstrumentFixtures.qsModules("AAPL");
        var resolved = Resolver.resolve(new Payload(Symbol.of("AAPL"), Optional.empty(), modules), CoreSpecs.CORE);
        assertThat(resolved.missingRequired()).containsExactly("firstTradeDate", "hasPrePostMarketData");
    }

    @Test
    void penceQuotedCurrencyIsKeptNotNulled() throws Exception {
        var row = YahooObjectMapper.create().readTree(InstrumentFixtures.v7Row("AAPL").toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) row).put("currency", "GBp");
        var resolved = Resolver.resolve(new Payload(Symbol.of("BP.L"), Optional.of(row), Map.of()), CoreSpecs.CORE);
        var core = CoreBuilder.build(resolved);
        assertThat(core.currency().code()).isEqualTo("GBp");
        assertThat(core.currency().iso()).isEmpty();
        assertThat(core.currency().isPence()).isTrue();
    }
}
