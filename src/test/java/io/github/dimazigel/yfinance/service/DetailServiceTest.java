package io.github.dimazigel.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.dimazigel.yfinance.api.QuoteApi;
import io.github.dimazigel.yfinance.api.QuoteSummaryApi;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.http.RawQuoteClient;
import io.github.dimazigel.yfinance.http.YahooJsonMapper;
import io.github.dimazigel.yfinance.instrument.Crypto;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.testsupport.Instruments;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.testsupport.YahooDispatcher;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class DetailServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private MockWebServer server;
    private DetailService service;
    private Equity aapl;
    private Etf spy;
    private Crypto btc;

    @BeforeEach
    void setUp() throws Exception {
        aapl = instrumentOf("AAPL", Equity.class);
        spy = instrumentOf("SPY", Etf.class);
        btc = instrumentOf("BTC-USD", Crypto.class);

        server = new MockWebServer();
        server.start();
        server.setDispatcher(defaultDispatcher());
        var client = new RawQuoteClient(Fixtures.api(server, QuoteApi.class), Fixtures.api(server, QuoteSummaryApi.class));
        service = new DetailService(client, Clock.fixed(NOW, ZoneOffset.UTC), 4);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void appleDetailIsOk() {
        assertThat(service.equity(aapl).orElseThrow().profile().sector()).isEqualTo("Technology");
    }

    @Test
    void spyDetailIsOkAndCryptoDetailIsOk() {
        assertThat(service.etf(spy).orElseThrow().legalType()).isNotBlank();
        assertThat(service.crypto(btc).orElseThrow().name()).isEqualTo("Bitcoin");
    }

    @Test
    void detailForVanishedSymbolIsSkippedNotFailed() { // Review Focus 5
        Equity gone = Instruments.withSymbol(aapl, "GONE");
        var outcome = service.equity(gone);
        assertThat(outcome).isInstanceOfSatisfying(Outcome.Skipped.class, s -> {
            assertThat(s.reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL);
            assertThat(s.detail()).as("a 200 with a null result reads the same as a 404 here").isEqualTo("quoteSummary has no result");
        });
    }

    @Test
    void missingGuaranteedModuleIsSkippedWithFieldNames() throws Exception {
        MockWebServer stripped = new MockWebServer();
        stripped.start();
        stripped.setDispatcher(dispatcherWithout("AAPL", "financialData"));
        try {
            var client = new RawQuoteClient(Fixtures.api(stripped, QuoteApi.class), Fixtures.api(stripped, QuoteSummaryApi.class));
            var strippedService = new DetailService(client, Clock.fixed(NOW, ZoneOffset.UTC), 4);

            var outcome = strippedService.equity(aapl);

            assertThat(outcome).isInstanceOfSatisfying(Outcome.Skipped.class, s -> {
                assertThat(s.reason()).isEqualTo(SkipReason.MODULE_ABSENT);
                assertThat(s.detail()).contains("financials.totalRevenue");
            });
        } finally {
            stripped.shutdown();
        }
    }

    @Test
    void batchKeepsOrderAndIsolatesFailures() throws Exception {
        Equity plug = instrumentOf("PLUG", Equity.class);
        Equity gone = Instruments.withSymbol(aapl, "GONE");

        var batch = service.equities(List.of(aapl, gone, plug));

        assertThat(batch.outcomes()).hasSize(3);
        assertThat(batch.outcomes().get(0)).isInstanceOf(Outcome.Ok.class);
        assertThat(batch.outcomes().get(1)).isInstanceOf(Outcome.Skipped.class);
        assertThat(batch.outcomes().get(2)).isInstanceOf(Outcome.Ok.class);
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void requestsOnlyTheClassModules() throws Exception {
        service.etf(spy);

        RecordedRequest request = server.takeRequest();
        String modules = request.getRequestUrl().queryParameter("modules");
        assertThat(modules).contains("fundProfile");
        assertThat(modules).doesNotContain("financialData");
    }

    @Test
    void logsSummaryAndDebugSkipDetail() throws Exception {
        MockWebServer stripped = new MockWebServer();
        stripped.start();
        stripped.setDispatcher(dispatcherWithout("AAPL", "financialData"));
        try {
            var client = new RawQuoteClient(Fixtures.api(stripped, QuoteApi.class), Fixtures.api(stripped, QuoteSummaryApi.class));
            var strippedService = new DetailService(client, Clock.fixed(NOW, ZoneOffset.UTC), 4);

            try (var log = LogCapture.of(DetailService.class)) {
                strippedService.equity(aapl);

                assertThat(log.messages(Level.INFO)).as("a single-instrument lookup is not a batch").isEmpty();
                assertThat(log.messages(Level.DEBUG))
                        .contains("details: 1 symbols: 0 ok, 1 skipped, 0 failed")
                        .anySatisfy(m -> assertThat(m).contains("AAPL detail skipped: missing").contains("financials.totalRevenue"));

                // The DEBUG skip line runs on the fanOut worker thread; its MDC must still carry
                // the per-instrument scope opened inside one(...), not just the batch-level one.
                ILoggingEvent debugEvent =
                        log.events().stream().filter(e -> e.getLevel() == Level.DEBUG).findFirst().orElseThrow();
                assertThat(debugEvent.getMDCPropertyMap())
                        .containsEntry(LogContext.OP, "details")
                        .containsEntry(LogContext.SYMBOL, "AAPL");
            }
        } finally {
            stripped.shutdown();
        }
    }

    @Test
    void logsOneInfoSummaryPerMultiSymbolBatch() throws Exception {   // final review, finding 4
        Equity plug = instrumentOf("PLUG", Equity.class);
        try (var log = LogCapture.of(DetailService.class)) {
            service.equities(List.of(aapl, plug));

            assertThat(log.messages(Level.INFO)).containsExactly("details: 2 symbols: 2 ok, 0 skipped, 0 failed");
        }
    }

    private static <I extends Instrument> I instrumentOf(String symbol, Class<I> as) throws Exception {
        MockWebServer setup = new MockWebServer();
        setup.start();
        setup.setDispatcher(defaultDispatcher());
        try {
            var client = new RawQuoteClient(Fixtures.api(setup, QuoteApi.class), Fixtures.api(setup, QuoteSummaryApi.class));
            var instruments = new InstrumentService(client, Clock.fixed(NOW, ZoneOffset.UTC));
            return instruments.instruments(List.of(Symbol.of(symbol)), as).outcomes().getFirst().orElseThrow();
        } finally {
            setup.shutdown();
        }
    }

    private static Dispatcher defaultDispatcher() {
        return new YahooDispatcher();
    }

    /** As {@link #defaultDispatcher()}, but {@code moduleToDrop} is stripped from {@code symbol}'s captured response. */
    private static Dispatcher dispatcherWithout(String symbol, String moduleToDrop) {
        Dispatcher fallback = defaultDispatcher();
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                var url = request.getRequestUrl();
                if (url.encodedPath().equals("/v7/finance/quote") || !url.pathSegments().getLast().equals(symbol)) {
                    return fallback.dispatch(request);
                }
                String body = Fixtures.load("instruments/qs_" + InstrumentFixtures.safe(symbol) + ".json");
                return new MockResponse().setResponseCode(200).setBody(withoutModule(body, moduleToDrop));
            }
        };
    }

    private static String withoutModule(String body, String moduleName) {
        JsonMapper mapper = YahooJsonMapper.create();
        JsonNode root = mapper.readTree(body);
        ((ObjectNode) root.at("/quoteSummary/result/0")).remove(moduleName);
        return mapper.writeValueAsString(root);
    }
}
