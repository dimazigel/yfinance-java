package io.github.dimazigel.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.api.QuoteApi;
import io.github.dimazigel.yfinance.api.QuoteSummaryApi;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.http.RawQuoteClient;
import io.github.dimazigel.yfinance.instrument.*;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.testsupport.YahooDispatcher;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstrumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private MockWebServer server;
    private InstrumentService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        server.setDispatcher(new YahooDispatcher());
        var client = new RawQuoteClient(Fixtures.api(server, QuoteApi.class), Fixtures.api(server, QuoteSummaryApi.class));
        service = new InstrumentService(client, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void classifiesEveryClassInOneBatchWithoutFallbackRequests() {
        var batch = service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("SPY"), Symbol.of("VFIAX"), Symbol.of("^GSPC"),
                Symbol.of("BTC-USD"), Symbol.of("EURUSD=X"), Symbol.of("ES=F")));

        assertThat(batch.values()).extracting(Instrument::assetClass).containsExactly(AssetClass.EQUITY, AssetClass.ETF,
                AssetClass.MUTUAL_FUND, AssetClass.INDEX, AssetClass.CRYPTO, AssetClass.FX, AssetClass.FUTURE);
        assertThat(batch.values()).allSatisfy(i -> assertThat(i.fetchedAt()).isEqualTo(NOW));
        assertThat(server.getRequestCount()).as("one v7 call, no quoteSummary").isEqualTo(1);
    }

    @Test
    void unknownSymbolIsSkippedNotFailed() {
        var batch = service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("NO_SUCH_SYMBOL_XYZ")));
        assertThat(batch.get(Symbol.of("NO_SUCH_SYMBOL_XYZ"))).containsInstanceOf(Outcome.Skipped.class)
                .get().satisfies(o -> assertThat(((Outcome.Skipped<Instrument>) o).reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL));
        assertThat(batch.failed()).isEmpty();
        assertThatThrownBy(() -> service.instrument(Symbol.of("NO_SUCH_SYMBOL_XYZ"))).isInstanceOf(YFDataException.class);
    }

    @Test
    void ucitsEtfTriggersExactlyOneFallbackRequestAndClassifies() {
        var batch = service.instruments(List.of(Symbol.of("CSPX.L"), Symbol.of("SPY")));
        assertThat(batch.values()).allSatisfy(i -> assertThat(i).isInstanceOf(Etf.class));
        assertThat(server.getRequestCount()).as("v7 + one quoteSummary for CSPX.L only").isEqualTo(2);
    }

    @Test
    void preferredShareIsDowngradedWithTheMissingFieldNamed() {
        Instrument bacpl = service.instrument(Symbol.of("BAC-PL"));
        assertThat(bacpl).isInstanceOfSatisfying(Unclassified.class, u -> {
            assertThat(u.attempted()).contains(AssetClass.EQUITY);
            assertThat(u.missing()).contains("marketCap");
            assertThat(u.core().price()).isPositive();   // the universal core is still there
        });
        assertThat(server.getRequestCount()).as("fallback was tried before downgrading").isEqualTo(2);
    }

    @Test
    void deadQuoteTypeNoneIsUnknown() {
        var batch = service.instruments(List.of(Symbol.of("RIDE")));
        assertThat(batch.skipped()).singleElement().satisfies(s -> assertThat(s.reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL));
    }

    @Test
    void typedOverloadSkipsOtherClassesAndDowngrades() {
        var equities = service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("SPY"), Symbol.of("BAC-PL")), Equity.class);
        assertThat(equities.values()).singleElement().satisfies(e -> assertThat(e.symbol()).isEqualTo(Symbol.of("AAPL")));
        assertThat(equities.skipped()).extracting(Outcome.Skipped::reason).containsExactly(SkipReason.WRONG_ASSET_CLASS, SkipReason.DOWNGRADED);
    }

    @Test
    void matchesRowsToRequestedSymbolsCaseInsensitively() {   // Review Focus 2
        var batch = service.instruments(List.of(Symbol.of("aapl")));
        assertThat(batch.values()).singleElement().satisfies(i -> assertThat(i.symbol()).isEqualTo(Symbol.of("AAPL")));
    }

    @Test
    void duplicatesYieldDuplicateOutcomes() {   // Review Focus 3
        var batch = service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("AAPL")));
        assertThat(batch.size()).isEqualTo(2);
        assertThat(batch.values()).hasSize(2);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void emptyInputMakesNoRequest() {   // Review Focus 3
        assertThat(service.instruments(List.of()).size()).isZero();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void transportFailureOnFallbackIsFailedNotThrown() throws Exception {
        server.shutdown();   // v7 itself fails: every symbol Failed, nothing thrown
        var batch = service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("SPY")));
        assertThat(batch.failed()).hasSize(2);
    }

    @Test
    void logsOneSummaryPerBatchAndDowngradesAtDebug() {
        try (var log = LogCapture.of(InstrumentService.class)) {
            service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("BAC-PL"), Symbol.of("RIDE")));

            assertThat(log.messages(Level.INFO)).containsExactly("instruments: 3 symbols: 2 ok, 1 skipped, 0 failed");
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m).startsWith("BAC-PL downgraded from EQUITY: missing [marketCap"));
            assertThat(log.messages(Level.WARN)).isEmpty();
        }
    }

    @Test
    void logsTheSummaryWhenTheWholeBatchFails() throws Exception {
        server.shutdown();
        try (var log = LogCapture.of(InstrumentService.class)) {
            service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("SPY")));
            assertThat(log.messages(Level.INFO)).containsExactly("instruments: 2 symbols: 0 ok, 0 skipped, 2 failed");
        }
    }
}
