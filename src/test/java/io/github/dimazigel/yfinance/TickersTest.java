package io.github.dimazigel.yfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.dimazigel.yfinance.api.YahooApis;
import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFRateLimitException;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.market.Dividend;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.testsupport.YahooDispatcher;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TickersTest {

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
    void fetchFansOutAnyTickerMethodIntoABatch() {
        Batch<List<Dividend>> batch = yf.tickers("AAPL", "MSFT").fetch(Ticker::dividends);

        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(Symbol.of("AAPL"), Symbol.of("MSFT"));
        assertThat(batch.values()).hasSize(2).allSatisfy(dividends -> assertThat(dividends).hasSize(1));
        assertThat(batch.get(Symbol.of("MSFT"))).containsInstanceOf(Outcome.Ok.class);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void fetchTurnsALibraryExceptionIntoFailedWithThatException() {
        var rateLimited = new YFRateLimitException("slow down", null);

        var batch = yf.tickers("AAPL", "MSFT").fetch(t -> {
            if (t.symbol().value().equals("MSFT")) {
                throw rateLimited;
            }
            return t.symbol().value();
        });

        assertThat(batch.values()).containsExactly("AAPL");
        assertThat(batch.failed()).singleElement().satisfies(f -> {
            assertThat(f.symbol()).isEqualTo(Symbol.of("MSFT"));
            assertThat(f.error()).isSameAs(rateLimited);
        });
        assertThat(batch.skipped()).as("fetch never skips").isEmpty();
    }

    @Test
    void fetchTurnsNullAndRuntimeFailuresIntoFailed() {
        var batch = yf.tickers("AAPL", "MSFT").fetch(t -> {
            if (t.symbol().value().equals("AAPL")) {
                return null;
            }
            throw new IllegalStateException("mapper bug");
        });

        assertThat(batch.get(Symbol.of("AAPL"))).get().isInstanceOfSatisfying(Outcome.Failed.class,
                f -> assertThat(f.error()).isInstanceOf(YFDataException.class).hasMessageContaining("no data"));
        assertThat(batch.get(Symbol.of("MSFT"))).get().isInstanceOfSatisfying(Outcome.Failed.class,
                f -> assertThat(f.error()).isInstanceOf(YFDataException.class).hasCauseInstanceOf(IllegalStateException.class));
        assertThat(batch.values()).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void instrumentsIsOneBatchedRequestNotAFanOut() {
        var batch = yf.tickers("AAPL", "SPY", YahooDispatcher.UNKNOWN).instruments();

        assertThat(batch.size()).isEqualTo(3);
        assertThat(batch.values()).extracting(Instrument::assetClass).containsExactly(AssetClass.EQUITY, AssetClass.ETF);
        assertThat(batch.skipped()).hasSize(1);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void historiesKeepOrderAndIsolateFailures() {
        var batch = yf.tickers("AAPL", YahooDispatcher.UNKNOWN).histories(Range.ONE_MONTH, Interval.ONE_DAY);

        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(Symbol.of("AAPL"), Symbol.of(YahooDispatcher.UNKNOWN));
        assertThat(batch.values()).singleElement().satisfies(h -> assertThat(h.bars()).hasSize(3));
        assertThat(batch.failed()).singleElement().satisfies(f -> assertThat(f.error()).isInstanceOf(YFDataException.class));
        assertThatThrownBy(() -> batch.outcomes().getLast().orElseThrow()).isInstanceOf(YFDataException.class);
    }

    @Test
    void fanOutRespectsConcurrencyBound() {
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();
        server.setDispatcher(new YahooDispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                int current = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlight.decrementAndGet();
                }
                return super.dispatch(request);
            }
        });

        var batch = yf.tickers("AAPL", "MSFT", "GOOG", "AMZN", "META", "NFLX")
                .withConcurrency(2)
                .histories(Range.ONE_MONTH, Interval.ONE_DAY);

        assertThat(batch.values()).hasSize(6);
        assertThat(maxInFlight.get()).isLessThanOrEqualTo(2);
    }

    @Test
    void concurrencyMustBePositive() {
        var tickers = yf.tickers("AAPL");
        assertThatThrownBy(() -> tickers.withConcurrency(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(tickers.ticker(Symbol.of("MSFT")).symbol()).isEqualTo(Symbol.of("MSFT"));
    }

    @Test
    void emptyTickersFetchNothing() {
        var batch = yf.tickers().fetch(Ticker::dividends);

        assertThat(batch.size()).isZero();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void fanOutLogsASummaryAndEachFailureOnTheWorkerThreadWithItsScope() {
        try (var log = LogCapture.of(Tickers.class)) {
            yf.tickers("AAPL", YahooDispatcher.UNKNOWN).histories(Range.ONE_MONTH, Interval.ONE_DAY);

            assertThat(log.messages(Level.INFO)).singleElement().satisfies(m ->
                    assertThat(m).matches("Fetched 2 symbols: 1 ok, 1 failed in \\d+ ms"));
            assertThat(log.messages(Level.DEBUG)).singleElement().satisfies(m ->
                    assertThat(m).startsWith(YahooDispatcher.UNKNOWN + " failed: YF").contains("Exception"));
            assertThat(log.messages(Level.WARN)).isEmpty();

            // MDC is thread-local: the failure line runs on the fan-out worker, whose scope must name the symbol.
            ILoggingEvent failure = log.events().stream().filter(e -> e.getLevel() == Level.DEBUG).findFirst().orElseThrow();
            assertThat(failure.getMDCPropertyMap())
                    .containsEntry(LogContext.OP, "fetch")
                    .containsEntry(LogContext.SYMBOL, YahooDispatcher.UNKNOWN);
        }
    }
}
