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
import io.github.dimazigel.yfinance.http.YahooJsonMapper;
import io.github.dimazigel.yfinance.instrument.*;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.InstrumentFixtures;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.testsupport.YahooDispatcher;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class InstrumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final JsonMapper JSON = YahooJsonMapper.create();
    private MockWebServer server;
    private RawQuoteClient client;
    private InstrumentService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        server.setDispatcher(new YahooDispatcher());
        client = new RawQuoteClient(Fixtures.api(server, QuoteApi.class), Fixtures.api(server, QuoteSummaryApi.class));
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
    void ucitsEtfTriggersExactlyOneFallbackRequestAndClassifies() throws Exception {
        // CSPX.L's v7 row has neither ytdReturn nor trailingThreeMonthReturns; both live only in
        // fundPerformance.trailingReturns (qs_CSPX_L.json), so the fallback must actually request
        // that module — not just the base price/summaryDetail/quoteType trio — or this always
        // downgrades, as the 282-symbol live survey found for every UCITS ETF before this fix.
        var batch = service.instruments(List.of(Symbol.of("CSPX.L"), Symbol.of("SPY")));
        assertThat(batch.values()).allSatisfy(i -> assertThat(i).isInstanceOf(Etf.class));
        assertThat(server.getRequestCount()).as("v7 + one quoteSummary for CSPX.L only").isEqualTo(2);

        server.takeRequest(); // the v7 batch call
        String modules = server.takeRequest().getRequestUrl().queryParameter("modules");
        assertThat(modules).contains("fundPerformance");
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
    void aFailedChunkFailsOnlyItsOwnSymbols() throws Exception {   // robustness review, item 5
        var v7Calls = new AtomicInteger();
        server.setDispatcher(new YahooDispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getRequestUrl().encodedPath().equals("/v7/finance/quote") && v7Calls.incrementAndGet() == 2) {
                    return new MockResponse().setResponseCode(500).setBody("<html>Yahoo! - Error report</html>");
                }
                return super.dispatch(request);
            }
        });
        var symbols = new ArrayList<Symbol>();
        symbols.add(Symbol.of("AAPL"));                                              // chunk 1: AAPL + 99 unknown
        IntStream.range(1, 249).forEach(i -> symbols.add(Symbol.of("S" + i)));       // chunk 2: 100 unknown (500s)
        symbols.add(Symbol.of("SPY"));                                               // chunk 3: 49 unknown + SPY

        var batch = service.instruments(symbols);

        assertThat(batch.size()).isEqualTo(250);
        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactlyElementsOf(symbols);
        assertThat(batch.values()).extracting(Instrument::symbol).containsExactly(Symbol.of("AAPL"), Symbol.of("SPY"));
        assertThat(batch.failed()).hasSize(100).allSatisfy(f -> {
            assertThat(Integer.parseInt(f.symbol().value().substring(1))).isBetween(100, 199);
            assertThat(f.error().getMessage()).contains("500");
        });
        assertThat(batch.skipped()).hasSize(148).allSatisfy(s -> assertThat(s.reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL));
        assertThat(server.getRequestCount()).as("three chunks, no fallbacks").isEqualTo(3);
        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols").split(",")).hasSize(100);
        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols").split(",")).hasSize(100);
        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols").split(",")).hasSize(50);
    }

    @Test
    void fallbacksRunConcurrentlyAndAreCounted() {   // robustness review, item 5
        // Three UCITS-style ETFs (CSPX.L's row under three symbols), each needing the quoteSummary
        // fallback; the fallbacks are fanned out with the configured concurrency, not run one after
        // another on the calling thread.
        Duration latency = Duration.ofMillis(300);
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();
        server.setDispatcher(new YahooDispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getRequestUrl().encodedPath();
                if (path.equals("/v7/finance/quote")) {
                    var rows = JSON.createArrayNode();
                    for (String symbol : request.getRequestUrl().queryParameter("symbols").split(",")) {
                        ObjectNode row = (ObjectNode) InstrumentFixtures.v7Row("CSPX.L").deepCopy();
                        rows.add(row.put("symbol", symbol));
                    }
                    var body = JSON.createObjectNode();
                    body.putObject("quoteResponse").set("result", rows);
                    return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body.toString());
                }
                if (path.startsWith("/v10/finance/quoteSummary/")) {
                    maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                    try {
                        Thread.sleep(latency.toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        inFlight.decrementAndGet();
                    }
                    return Fixtures.jsonResponse("instruments/qs_CSPX_L.json");
                }
                return super.dispatch(request);
            }
        });
        var concurrent = new InstrumentService(client, Clock.fixed(NOW, ZoneOffset.UTC), 3);
        var symbols = List.of(Symbol.of("X1.L"), Symbol.of("X2.L"), Symbol.of("X3.L"));

        try (var log = LogCapture.of(InstrumentService.class)) {
            var batch = concurrent.instruments(symbols);

            assertThat(batch.values()).hasSize(3).allSatisfy(i -> assertThat(i).isInstanceOf(Etf.class));
            assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactlyElementsOf(symbols);
            assertThat(server.getRequestCount()).as("one v7 call + one fallback per symbol").isEqualTo(4);
            assertThat(maxInFlight.get()).as("fallbacks overlap").isGreaterThan(1);
            assertThat(log.messages(Level.INFO)).containsExactly("instruments: 3 symbols: 3 ok, 0 skipped, 0 failed; fallbacks=3");
        }
    }

    @Test
    void logsOneSummaryPerBatchAndDowngradesAtDebug() {
        try (var log = LogCapture.of(InstrumentService.class)) {
            service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("BAC-PL"), Symbol.of("RIDE")));

            // BAC-PL (preferred share) and RIDE (quoteType NONE, no price) each cost a fallback request
            assertThat(server.getRequestCount()).isEqualTo(3);
            assertThat(log.messages(Level.INFO)).containsExactly("instruments: 3 symbols: 2 ok, 1 skipped, 0 failed; fallbacks=2");
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m).startsWith("BAC-PL downgraded from EQUITY: missing [marketCap"));
            assertThat(log.messages(Level.WARN)).isEmpty();
        }
    }

    @Test
    void singleSymbolLookupLogsItsSummaryAtDebugNotInfo() {   // final review, finding 4
        try (var log = LogCapture.of(InstrumentService.class)) {
            service.instrument(Symbol.of("AAPL"));

            assertThat(log.messages(Level.INFO)).as("INFO is per batch, never per symbol").isEmpty();
            assertThat(log.messages(Level.DEBUG)).contains("instruments: 1 symbols: 1 ok, 0 skipped, 0 failed; fallbacks=0");
        }
    }

    @Test
    void logsTheSummaryWhenTheWholeBatchFails() throws Exception {
        server.shutdown();
        try (var log = LogCapture.of(InstrumentService.class)) {
            service.instruments(List.of(Symbol.of("AAPL"), Symbol.of("SPY")));
            assertThat(log.messages(Level.INFO)).containsExactly("instruments: 2 symbols: 0 ok, 0 skipped, 2 failed; fallbacks=0");
        }
    }
}
