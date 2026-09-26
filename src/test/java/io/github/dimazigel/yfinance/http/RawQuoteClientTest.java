package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.api.QuoteApi;
import io.github.dimazigel.yfinance.api.QuoteSummaryApi;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RawQuoteClientTest {

    private MockWebServer server;
    private RawQuoteClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new RawQuoteClient(Fixtures.api(server, QuoteApi.class), Fixtures.api(server, QuoteSummaryApi.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void quoteRowsAreKeyedByRequestedSymbolCaseInsensitively() throws Exception {
        server.enqueue(Fixtures.jsonResponse("instruments/v7_AAPL.json"));

        var rows = client.quoteRows(List.of(Symbol.of("aapl"), Symbol.of("NOPE")));

        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols")).isEqualTo("AAPL,NOPE");
        assertThat(rows).containsOnlyKeys(Symbol.of("AAPL"));          // Symbol normalises case; NOPE absent = unknown
        assertThat(rows.get(Symbol.of("AAPL")).path("quoteType").asString()).isEqualTo("EQUITY");
    }

    @Test
    void quoteRowsChunkAtOneHundred() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"quoteResponse\":{\"result\":[],\"error\":null}}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"quoteResponse\":{\"result\":[],\"error\":null}}"));
        var symbols = IntStream.range(0, 150).mapToObj(i -> Symbol.of("S" + i)).toList();

        client.quoteRows(symbols);

        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols").split(",")).hasSize(100);
        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols").split(",")).hasSize(50);
    }

    @Test
    void emptyInputMakesNoRequest() {
        assertThat(client.quoteRows(List.of())).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void modulesReturnsTheModulesOfTheFirstResult() throws Exception {
        server.enqueue(Fixtures.jsonResponse("instruments/qs_AAPL.json"));

        var modules = client.modules(Symbol.of("AAPL"), List.of("price", "summaryDetail", "quoteType"));

        var url = server.takeRequest().getRequestUrl();
        assertThat(url.encodedPath()).isEqualTo("/v10/finance/quoteSummary/AAPL");
        assertThat(url.queryParameter("modules")).isEqualTo("price,summaryDetail,quoteType");
        assertThat(url.queryParameter("formatted")).isEqualTo("false");
        assertThat(modules).isPresent();
        assertThat(modules.get()).containsKeys("price", "summaryDetail", "quoteType", "financialData");
        assertThat(modules.get().get("price").path("regularMarketPrice").isNumber()).isTrue();
    }

    @Test
    void modulesIsEmptyForUnknownSymbolAndPropagatesOtherErrors() {
        server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json")
                .setBody("{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"Quote not found for symbol: NOPE\"}}}"));
        assertThat(client.modules(Symbol.of("NOPE"), List.of("price"))).isEmpty();

        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"quoteSummary\":{\"result\":null,\"error\":null}}"));
        assertThat(client.modules(Symbol.of("NOPE"), List.of("price"))).as("200 with null result").isEmpty();

        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.modules(Symbol.of("AAPL"), List.of("price")))
                .isInstanceOf(io.github.dimazigel.yfinance.exception.YFHttpException.class);
    }
}
