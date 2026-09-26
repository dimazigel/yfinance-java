package io.github.dimazigel.yfinance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.api.YahooApis;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.model.Info;
import io.github.dimazigel.yfinance.model.OptionChain;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.Dispatcher;
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
        var retrofit = Fixtures.retrofit(server.url("/"));
        yf = YFinance.fromApis(YahooApis.create(retrofit, retrofit));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fanOutRespectsConcurrencyBound() {
        var inFlight = new java.util.concurrent.atomic.AtomicInteger();
        var maxInFlight = new java.util.concurrent.atomic.AtomicInteger();
        server.setDispatcher(new Dispatcher() {
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
                return Fixtures.jsonResponse("quotesummary_aapl.json");
            }
        });

        var results = yf.tickers("AAPL", "MSFT", "GOOG", "AMZN", "META", "NFLX")
                .withConcurrency(2)
                .infos();

        assertThat(results).hasSize(6);
        assertThat(results.values()).allMatch(Tickers.Result::isSuccess);
        assertThat(maxInFlight.get()).isLessThanOrEqualTo(2);
    }

    @Test
    void infosReturnPerSymbolResultsAndNeverLoseSuccessesOnFailure() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().contains("MSFT")) {
                    return new MockResponse().setResponseCode(200).setBody(
                            "{\"quoteSummary\":{\"result\":null,"
                                    + "\"error\":{\"code\":\"Not Found\",\"description\":\"boom\"}}}");
                }
                return Fixtures.jsonResponse("quotesummary_aapl.json");
            }
        });

        var results = yf.tickers("AAPL", "MSFT").infos();

        assertThat(results).containsOnlyKeys(Symbol.of("AAPL"), Symbol.of("MSFT"));

        var aapl = results.get(Symbol.of("AAPL"));
        assertThat(aapl.isSuccess()).isTrue();
        assertThat(aapl).isInstanceOfSatisfying(Tickers.Result.Success.class,
                ok -> assertThat(((Info) ok.value()).profile().sector()).isEqualTo("Technology"));
        assertThat(aapl.toOptional()).isPresent();

        var msft = results.get(Symbol.of("MSFT"));
        assertThat(msft.isSuccess()).isFalse();
        assertThat(msft.toOptional()).isEmpty();
        assertThat(msft).isInstanceOfSatisfying(Tickers.Result.Failure.class,
                failed -> assertThat(failed.error()).isInstanceOf(YFDataException.class).hasMessageContaining("boom"));
    }

    @Test
    void fetchFansOutAnyTickerMethod() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return Fixtures.jsonResponse("options_aapl.json");
            }
        });

        Map<Symbol, Tickers.Result<OptionChain>> chains = yf.tickers("AAPL", "MSFT").fetch(Ticker::optionChain);

        assertThat(chains.keySet()).extracting(Symbol::value).containsExactly("AAPL", "MSFT");
        assertThat(chains.values()).allSatisfy(r -> assertThat(r.orElseThrow().calls()).hasSize(1));
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void fetchTurnsNullAndRuntimeFailuresIntoFailures() {
        var results = yf.tickers("AAPL", "MSFT").fetch(t -> {
            if (t.symbol().value().equals("AAPL")) {
                return null; // e.g. analystPriceTargets() for an index
            }
            throw new IllegalStateException("mapper bug");
        });

        assertThat(results.get(Symbol.of("AAPL"))).isInstanceOfSatisfying(Tickers.Result.Failure.class,
                f -> assertThat(f.error()).isInstanceOf(YFDataException.class).hasMessageContaining("no data"));
        assertThat(results.get(Symbol.of("MSFT"))).isInstanceOfSatisfying(Tickers.Result.Failure.class,
                f -> assertThat(f.error()).isInstanceOf(YFDataException.class).hasCauseInstanceOf(IllegalStateException.class));
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void resultIsSealedAndPatternMatchable() {
        List<Tickers.Result<String>> results = List.of(
                Tickers.Result.success(Symbol.of("AAPL"), "v"),
                Tickers.Result.failure(Symbol.of("X"), new YFDataException("nope")));

        // Exhaustive switch: no default branch needed because Result is sealed.
        var described = results.stream().map(r -> switch (r) {
            case Tickers.Result.Success<String> ok -> ok.symbol() + "=" + ok.value();
            case Tickers.Result.Failure<String> failed -> failed.symbol() + "!" + failed.error().getMessage();
        }).toList();

        assertThat(described).containsExactly("AAPL=v", "X!nope");
    }

    @Test
    void resultOrElseThrowReturnsValueOrRaises() {
        var ok = Tickers.Result.success(Symbol.of("AAPL"), "v");
        assertThat(ok.orElseThrow()).isEqualTo("v");

        var bad = Tickers.Result.<String>failure(Symbol.of("X"), new YFDataException("nope"));
        assertThat(bad.isSuccess()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(bad::orElseThrow)
                .isInstanceOf(YFDataException.class);
    }

    @Test
    void fanOutLogsASummaryAndEachFailure() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().contains("MSFT")) {
                    return new MockResponse().setResponseCode(200).setBody(
                            "{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"boom\"}}}");
                }
                return Fixtures.jsonResponse("quotesummary_aapl.json");
            }
        });

        try (var log = io.github.dimazigel.yfinance.testsupport.LogCapture.of(Tickers.class)) {
            yf.tickers("AAPL", "MSFT").infos();

            assertThat(log.messages(ch.qos.logback.classic.Level.INFO)).singleElement().satisfies(m ->
                    assertThat(m).matches("Fetched 2 symbols: 1 ok, 1 failed in \\d+ ms"));
            assertThat(log.messages(ch.qos.logback.classic.Level.DEBUG)).singleElement().satisfies(m ->
                    assertThat(m).startsWith("MSFT failed: YFDataException: ").contains("boom"));
        }
    }
}
