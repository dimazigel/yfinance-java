package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.specs.CryptoDetailSpecs;
import io.github.dimazigel.yfinance.detail.CryptoDetail;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class CryptoDetailBuilderTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final Symbol BTC = Symbol.of("BTC-USD");

    @Test
    void bitcoinDetailIsComplete() {
        var modules = InstrumentFixtures.qsModules("BTC-USD");
        var r = Resolver.resolve(new Payload(BTC, Optional.empty(), modules), CryptoDetailSpecs.DETAIL);
        assertThat(r.missingRequired()).isEmpty();

        CryptoDetail btc = CryptoDetailBuilder.build(r, BTC, NOW);
        assertThat(btc.name()).isEqualTo("Bitcoin");
        assertThat(btc.website().getHost()).isNotBlank();
        assertThat(btc.whitepaper()).isPresent();
        assertThat(btc.fullyDilutedValue()).isPositive();
    }

    @Test
    void malformedOptionalWhitepaperIsDroppedNotFatal() {   // final review, finding 6
        var modules = new HashMap<>(InstrumentFixtures.qsModules("BTC-USD"));
        ObjectNode profile = (ObjectNode) modules.get("assetProfile").deepCopy();
        profile.put("whitepaper", "not a uri, with spaces");
        modules.put("assetProfile", profile);
        var r = Resolver.resolve(new Payload(BTC, Optional.empty(), modules), CryptoDetailSpecs.DETAIL);

        CryptoDetail btc = CryptoDetailBuilder.build(r, BTC, NOW);

        assertThat(btc.whitepaper()).as("optional URL: lenient, like irWebsite and styleBoxUrl").isEmpty();
        assertThat(btc.name()).isEqualTo("Bitcoin");
    }
}
