package io.github.dimazigel.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.api.OptionsApi;
import io.github.dimazigel.yfinance.enums.OptionType;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OptionsServiceTest {

    private MockWebServer server;
    private OptionsService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        service = new OptionsService(Fixtures.api(server, OptionsApi.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void parsesOptionChainFromTheRealFixture() throws Exception {
        server.enqueue(Fixtures.jsonResponse("options/options_AAPL.json"));

        var chain = service.getOptionChain(Symbol.of("AAPL")).orElseThrow();

        assertThat(chain.underlyingSymbol()).isEqualTo(Symbol.of("AAPL"));
        assertThat(chain.expirationDates()).hasSize(23)
                .startsWith(Instant.ofEpochSecond(1790553600), Instant.ofEpochSecond(1790726400));
        assertThat(chain.expiration()).isEqualTo(Instant.ofEpochSecond(1790553600));
        assertThat(chain.calls()).hasSize(36);
        assertThat(chain.puts()).hasSize(36);

        // Every contract, regardless of survey result, is identified by these four by construction.
        assertThat(chain.calls()).allSatisfy(c -> {
            assertThat(c.contractSymbol()).isNotBlank();
            assertThat(c.type()).isEqualTo(OptionType.CALL);
            assertThat(c.strike()).isNotNull();
            assertThat(c.expiration()).isEqualTo(chain.expiration());
        });
        assertThat(chain.puts()).allSatisfy(p -> assertThat(p.type()).isEqualTo(OptionType.PUT));

        var firstItmCall = chain.calls().stream().filter(c -> c.inTheMoney()).findFirst().orElseThrow();
        assertThat(firstItmCall.contractSymbol()).isEqualTo("AAPL260928C00250000");
        assertThat(firstItmCall.strike()).isEqualByComparingTo("250.0");
        assertThat(firstItmCall.inTheMoney()).isTrue();

        // Keys the per-row survey found in 100% of 987 live contracts: non-null.
        assertThat(firstItmCall.currency()).isEqualTo(QuoteCurrency.of("USD"));
        assertThat(firstItmCall.lastPrice()).isEqualByComparingTo("89.85");
        assertThat(firstItmCall.change()).isEqualByComparingTo("9.389999");
        assertThat(firstItmCall.changePercent()).as("wire 11.670394 % stored as a fraction").isEqualByComparingTo("0.11670394");
        assertThat(firstItmCall.ask()).isEqualByComparingTo("92.7");
        assertThat(firstItmCall.contractSize()).isEqualTo("REGULAR");
        assertThat(firstItmCall.lastTradeDate()).isEqualTo(Instant.ofEpochSecond(1790354792));
        assertThat(firstItmCall.impliedVolatility()).isEqualByComparingTo("2.24365673461914");

        // Keys the survey found short of 100% (bid 99.7%, openInterest 97.2%, volume 95.4%): Optional,
        // but present on this contract.
        assertThat(firstItmCall.bid()).isPresent();
        assertThat(firstItmCall.bid().orElseThrow()).isEqualByComparingTo("89.25");
        assertThat(firstItmCall.openInterest()).contains(1L);
        assertThat(firstItmCall.volume()).contains(1L);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/v7/finance/options/AAPL");
        assertThat(req.getRequestUrl().queryParameter("date")).isNull();
    }

    @Test
    void requestsAndParsesANonNearestExpiration() throws Exception {
        server.enqueue(Fixtures.jsonResponse("options/options_AAPL_1790726400.json"));

        var chain = service.getOptionChain(Symbol.of("AAPL"), Instant.ofEpochSecond(1790726400)).orElseThrow();

        assertThat(chain.expiration()).isEqualTo(Instant.ofEpochSecond(1790726400));
        assertThat(chain.calls()).hasSize(36);
        assertThat(chain.puts()).hasSize(37);
        assertThat(chain.calls()).allSatisfy(c -> assertThat(c.expiration()).isEqualTo(chain.expiration()));

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().queryParameter("date")).isEqualTo("1790726400");
    }

    @Test
    void emptyExpirationDatesYieldNoChain() {
        server.enqueue(Fixtures.jsonResponse("options/options_empty.json"));

        assertThat(service.getOptionChain(Symbol.of("SAP.DE"))).isEmpty();
    }

    @Test
    void getExpirationDatesIsEmptyWhenThereIsNoChain() {
        server.enqueue(Fixtures.jsonResponse("options/options_empty.json"));

        assertThat(service.getExpirationDates(Symbol.of("SAP.DE"))).isEmpty();
    }

    @Test
    void dropsAContractMissingA100PercentKeyAndLogsTheCount() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"optionChain\":{\"result\":[{\"underlyingSymbol\":\"AAPL\","
                        + "\"expirationDates\":[1714752000],"
                        + "\"options\":[{\"expirationDate\":1714752000,"
                        + "\"calls\":["
                        + "{\"contractSymbol\":\"AAPL240503C00190000\",\"strike\":190.0,\"currency\":\"USD\","
                        + "\"lastPrice\":3.25,\"change\":0.15,\"percentChange\":4.8,\"volume\":12000,"
                        + "\"openInterest\":34000,\"bid\":3.2,\"ask\":3.3,\"contractSize\":\"REGULAR\","
                        + "\"expiration\":1714752000,\"lastTradeDate\":1714680000,\"impliedVolatility\":0.2351,"
                        + "\"inTheMoney\":true},"
                        + "{\"contractSymbol\":\"AAPL240503C00200000\",\"strike\":200.0,"
                        + "\"lastPrice\":1.1,\"change\":0.05,\"percentChange\":4.8,\"ask\":1.2,"
                        + "\"contractSize\":\"REGULAR\",\"expiration\":1714752000,\"lastTradeDate\":1714680000,"
                        + "\"impliedVolatility\":0.21,\"inTheMoney\":false}],"
                        + "\"puts\":[]}]}],\"error\":null}}"));

        try (var log = LogCapture.ofLibrary()) {
            var chain = service.getOptionChain(Symbol.of("AAPL")).orElseThrow();

            // The second call has no "currency", a 100% key, so it is dropped.
            assertThat(chain.calls()).hasSize(1);
            assertThat(chain.calls().getFirst().contractSymbol()).isEqualTo("AAPL240503C00190000");
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m)
                    .isEqualTo("Dropped 1 of 2 contracts without a complete required field"));
        }
    }
}
