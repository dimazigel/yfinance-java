package io.github.dimazigel.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.api.LookupApi;
import io.github.dimazigel.yfinance.api.SearchApi;
import io.github.dimazigel.yfinance.enums.LookupType;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.net.URI;
import java.time.Instant;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchLookupServiceTest {

    private MockWebServer server;
    private SearchService searchService;
    private LookupService lookupService;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        searchService = new SearchService(Fixtures.api(server, SearchApi.class));
        lookupService = new LookupService(Fixtures.api(server, LookupApi.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void parsesSearch() throws Exception {
        server.enqueue(Fixtures.jsonResponse("search_apple.json"));

        var result = searchService.search("apple");

        assertThat(result.quotes()).hasSize(2);
        assertThat(result.quotes().getFirst().symbol()).isEqualTo(Symbol.of("AAPL"));
        assertThat(result.quotes().getFirst().longName()).contains("Apple Inc.");
        assertThat(result.news()).singleElement().satisfies(n -> {
            assertThat(n.title()).contains("Apple announces new product");
            assertThat(n.link()).contains(URI.create("https://finance.yahoo.com/news/apple.html"));
            assertThat(n.publishTime()).contains(Instant.ofEpochSecond(1714680000));
        });

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/v1/finance/search");
        assertThat(req.getRequestUrl().queryParameter("q")).isEqualTo("apple");
    }

    @Test
    void parsesLookup() throws Exception {
        server.enqueue(Fixtures.jsonResponse("lookup_apple.json"));

        var quotes = lookupService.lookup("apple", LookupType.EQUITY, 25);

        assertThat(quotes).hasSize(2);
        assertThat(quotes.getFirst().symbol()).isEqualTo(Symbol.of("AAPL"));
        assertThat(quotes.getFirst().regularMarketPrice()).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("190.5"));

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/v1/finance/lookup");
        assertThat(req.getRequestUrl().queryParameter("query")).isEqualTo("apple");
        assertThat(req.getRequestUrl().queryParameter("type")).isEqualTo("equity");
        assertThat(req.getRequestUrl().queryParameter("count")).isEqualTo("25");
    }
}
