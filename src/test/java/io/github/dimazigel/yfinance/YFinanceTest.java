package io.github.dimazigel.yfinance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.api.YahooApis;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.LookupType;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Crypto;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.Instruments;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.testsupport.YahooDispatcher;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YFinanceTest {

    private MockWebServer server;
    private YFinance yf;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        server.setDispatcher(new YahooDispatcher());
        var retrofit = Fixtures.retrofit(server.url("/"));
        yf = YFinance.fromApis(YahooApis.create(retrofit, retrofit));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void instrumentsClassifiesEveryClassInOneRequest() {
        var batch = yf.instruments(symbols("AAPL", "SPY", "VFIAX", "^GSPC", "BTC-USD", "EURUSD=X", "ES=F", YahooDispatcher.UNKNOWN));

        assertThat(batch.size()).isEqualTo(8);
        assertThat(batch.values()).extracting(Instrument::assetClass).containsExactly(AssetClass.EQUITY, AssetClass.ETF,
                AssetClass.MUTUAL_FUND, AssetClass.INDEX, AssetClass.CRYPTO, AssetClass.FX, AssetClass.FUTURE);
        assertThat(batch.skipped()).singleElement().satisfies(s -> {
            assertThat(s.symbol()).isEqualTo(Symbol.of(YahooDispatcher.UNKNOWN));
            assertThat(s.reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL);
        });
        assertThat(server.getRequestCount()).as("one batched v7 request, no fallback").isEqualTo(1);
    }

    @Test
    void typedInstrumentsSkipOtherClasses() {
        var equities = yf.instruments(symbols("AAPL", "SPY"), Equity.class);

        assertThat(equities.values()).singleElement().satisfies(e -> assertThat(e.symbol()).isEqualTo(Symbol.of("AAPL")));
        assertThat(equities.skipped()).singleElement().satisfies(s -> assertThat(s.reason()).isEqualTo(SkipReason.WRONG_ASSET_CLASS));
    }

    @Test
    void detailsForEveryDetailClass() {
        Equity aapl = yf.ticker("AAPL").as(Equity.class);
        Etf spy = yf.ticker("SPY").as(Etf.class);
        MutualFund vfiax = yf.ticker("VFIAX").as(MutualFund.class);
        Crypto btc = yf.ticker("BTC-USD").as(Crypto.class);

        assertThat(yf.equityDetails(List.of(aapl)).values()).singleElement()
                .satisfies(d -> assertThat(d.profile().sector()).isEqualTo("Technology"));
        assertThat(yf.etfDetails(List.of(spy)).values()).singleElement()
                .satisfies(d -> assertThat(d.symbol()).isEqualTo(Symbol.of("SPY")));
        assertThat(yf.mutualFundDetails(List.of(vfiax)).values()).singleElement()
                .satisfies(d -> assertThat(d.symbol()).isEqualTo(Symbol.of("VFIAX")));
        assertThat(yf.cryptoDetails(List.of(btc)).values()).singleElement()
                .satisfies(d -> assertThat(d.name()).isEqualTo("Bitcoin"));
    }

    @Test
    void detailsKeepOrderAndSkipVanishedSymbols() {
        Equity aapl = yf.ticker("AAPL").as(Equity.class);
        Equity gone = Instruments.withSymbol(aapl, "GONE");

        var batch = yf.equityDetails(List.of(gone, aapl));

        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(Symbol.of("GONE"), Symbol.of("AAPL"));
        assertThat(batch.outcomes().getFirst()).isInstanceOfSatisfying(Outcome.Skipped.class,
                s -> assertThat(s.reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL));
        assertThat(batch.values()).hasSize(1);
    }

    @Test
    void optionsBatchIsOkForBothChainsAndNoListedOptions() {
        var batch = yf.options(symbols("AAPL", "EURUSD=X"));

        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(Symbol.of("AAPL"), Symbol.of("EURUSD=X"));
        assertThat(batch.failed()).isEmpty();
        assertThat(batch.values()).hasSize(2);
        assertThat(batch.values().get(0)).hasValueSatisfying(chain -> assertThat(chain.calls()).hasSize(36));
        assertThat(batch.values().get(1)).isEmpty();
    }

    @Test
    void historiesPreserveInputOrderAndIsolateFailures() {
        var batch = yf.histories(symbols("MSFT", YahooDispatcher.UNKNOWN, "AAPL"), Range.ONE_MONTH, Interval.ONE_DAY);

        assertThat(batch.outcomes()).extracting(Outcome::symbol)
                .containsExactly(Symbol.of("MSFT"), Symbol.of(YahooDispatcher.UNKNOWN), Symbol.of("AAPL"));
        assertThat(batch.values()).hasSize(2).allSatisfy(h -> assertThat(h.bars()).hasSize(3));
        assertThat(batch.failed()).singleElement().satisfies(f -> {
            assertThat(f.symbol()).isEqualTo(Symbol.of(YahooDispatcher.UNKNOWN));
            assertThat(f.error()).isInstanceOf(YFDataException.class);
        });
        assertThat(batch.skipped()).isEmpty();
    }

    @Test
    void statementsBatchUsesEachEquityAsItsOwnProof() {
        Equity aapl = yf.ticker("AAPL").as(Equity.class);
        Equity msft = Instruments.withSymbol(aapl, "MSFT");

        var batch = yf.statements(List.of(aapl, msft, aapl), StatementType.INCOME, Frequency.ANNUAL);

        assertThat(batch.outcomes()).extracting(Outcome::symbol)
                .containsExactly(Symbol.of("AAPL"), Symbol.of("MSFT"), Symbol.of("AAPL"));
        assertThat(batch.values()).hasSize(3).allSatisfy(s ->
                assertThat(s.value("TotalRevenue", LocalDate.parse("2023-09-30")).orElseThrow()).isEqualByComparingTo("383285000000"));
    }

    @Test
    void emptyInputsMakeNoRequest() {
        assertThat(yf.instruments(List.of()).size()).isZero();
        assertThat(yf.instruments(List.of(), Equity.class).size()).isZero();
        assertThat(yf.equityDetails(List.of()).size()).isZero();
        assertThat(yf.histories(List.of(), Range.ONE_MONTH, Interval.ONE_DAY).size()).isZero();
        assertThat(yf.statements(List.of(), StatementType.INCOME, Frequency.ANNUAL).size()).isZero();
        assertThat(yf.options(List.of()).size()).isZero();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void tickersAreBuiltFromStringsOrSymbols() {
        assertThat(yf.tickers("aapl", "MSFT").symbols()).containsExactly(Symbol.of("AAPL"), Symbol.of("MSFT"));
        assertThat(yf.tickers(List.of(Symbol.of("SPY"))).symbols()).containsExactly(Symbol.of("SPY"));
        assertThat(yf.ticker(Symbol.of("AAPL")).symbol()).isEqualTo(Symbol.of("AAPL"));
    }

    @Test
    void facadeExposesSearchAndLookup() {
        var result = yf.search("apple");
        assertThat(result.quotes()).hasSize(2);
        assertThat(result.quotes().getFirst().longName()).contains("Apple Inc.");

        var quotes = yf.lookup("apple", LookupType.EQUITY);
        assertThat(quotes).hasSize(2);
        assertThat(quotes.getFirst().regularMarketPrice()).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("190.5"));
    }

    @Test
    void yFinanceIsCloseable() {
        var retrofit = Fixtures.retrofit(server.url("/"));
        YFinance closeable = YFinance.fromApis(YahooApis.create(retrofit, retrofit));
        closeable.close(); // no-op for fromApis, must not throw
        closeable.close(); // idempotent
    }

    @Test
    void createLogsEffectiveConfigAndCloseLogsOnce() {
        var config = EndpointConfig.production()
                .withHosts(server.url("/"))
                .withCallTimeout(Duration.ofSeconds(7));

        try (var log = LogCapture.of(YFinance.class)) {
            YFinance created = YFinance.create(config); // no request yet: the crumb handshake is lazy
            created.close();

            assertThat(log.messages(Level.INFO)).singleElement().satisfies(m -> assertThat(m)
                    .startsWith("yfinance-java client created:")
                    .contains("callTimeout=PT7S")
                    .contains("rateLimit=on/3 attempts")
                    .contains("retry5xx=3 attempts")
                    .contains("customizer=no"));
            assertThat(log.messages(Level.DEBUG)).containsExactly("yfinance-java client closed");
        }
    }

    @Test
    void fanOutConcurrencyFromTheConfigBoundsDetailRequestsAndSeedsTickers() {   // final review, finding 5
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();
        server.setDispatcher(new YahooDispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (!request.getRequestUrl().encodedPath().startsWith("/v10/finance/quoteSummary/")) {
                    return super.dispatch(request);
                }
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlight.decrementAndGet();
                }
                return super.dispatch(request);
            }
        });
        Equity aapl = Instruments.equity("AAPL");
        Equity plug = Instruments.equity("PLUG");
        var config = EndpointConfig.production().withHosts(server.url("/"));

        try (var serial = YFinance.create(config.withFanOutConcurrency(1))) {
            var batch = serial.equityDetails(List.of(aapl, plug, aapl, plug));
            assertThat(batch.values()).hasSize(4);
            assertThat(maxInFlight.get()).as("one quoteSummary request at a time").isEqualTo(1);
            assertThat(serial.tickers("AAPL", "PLUG").concurrency()).as("Tickers default follows the config").isEqualTo(1);
            assertThat(serial.tickers("AAPL").withConcurrency(3).concurrency()).as("per-instance override still wins").isEqualTo(3);
        }

        maxInFlight.set(0);
        try (var parallel = YFinance.create(config.withFanOutConcurrency(4))) {
            var batch = parallel.equityDetails(List.of(aapl, plug, aapl, plug));
            assertThat(batch.values()).hasSize(4);
            assertThat(maxInFlight.get()).as("four permits: the requests overlap").isGreaterThan(1);
            assertThat(parallel.tickers("AAPL").concurrency()).isEqualTo(4);
        }
    }

    private static List<Symbol> symbols(String... values) {
        return java.util.Arrays.stream(values).map(Symbol::of).toList();
    }
}
