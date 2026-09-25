package io.github.dimazigel.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.api.OptionsApi;
import io.github.dimazigel.yfinance.enums.OptionType;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.Currency;
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
    void parsesOptionChain() throws Exception {
        server.enqueue(Fixtures.jsonResponse("options_aapl.json"));

        var chain = service.getOptionChain(Symbol.of("AAPL"));

        assertThat(chain.expirationDates()).containsExactly(
                Instant.ofEpochSecond(1714752000),
                Instant.ofEpochSecond(1715356800),
                Instant.ofEpochSecond(1715961600));
        assertThat(chain.expiration()).isEqualTo(Instant.ofEpochSecond(1714752000));

        assertThat(chain.calls()).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(OptionType.CALL);
            assertThat(c.contractSymbol()).isEqualTo("AAPL240503C00190000");
            assertThat(c.strike()).isEqualByComparingTo("190.0");
            assertThat(c.lastPrice()).isEqualByComparingTo("3.25");
            assertThat(c.openInterest()).isEqualTo(34000L);
            assertThat(c.inTheMoney()).isTrue();
            assertThat(c.currency()).isEqualTo(Currency.getInstance("USD"));
            assertThat(c.lastTradeDate()).isEqualTo(Instant.ofEpochSecond(1714680000));
        });
        assertThat(chain.puts()).singleElement().satisfies(p -> {
            assertThat(p.type()).isEqualTo(OptionType.PUT);
            assertThat(p.inTheMoney()).isFalse();
        });

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/v7/finance/options/AAPL");
        assertThat(req.getRequestUrl().queryParameter("date")).isNull();
    }

    @Test
    void requestsSpecificExpiration() throws Exception {
        server.enqueue(Fixtures.jsonResponse("options_aapl.json"));

        service.getOptionChain(Symbol.of("AAPL"), Instant.ofEpochSecond(1715356800));

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().queryParameter("date")).isEqualTo("1715356800");
    }
}
