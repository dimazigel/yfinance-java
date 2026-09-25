package io.ziggy.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ziggy.yfinance.api.QuoteApi;
import io.ziggy.yfinance.api.QuoteSummaryApi;
import io.ziggy.yfinance.exception.YFDataException;
import io.ziggy.yfinance.testsupport.Fixtures;
import io.ziggy.yfinance.valueobject.Symbol;
import java.util.List;
import java.net.URI;
import java.time.Instant;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuoteServiceTest {

    private MockWebServer server;
    private QuoteService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        service = new QuoteService(Fixtures.api(server, QuoteSummaryApi.class), Fixtures.api(server, QuoteApi.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void parsesProfileAndQuote() throws Exception {
        server.enqueue(Fixtures.jsonResponse("quotesummary_aapl.json"));

        var info = service.getInfo(Symbol.of("AAPL"));

        assertThat(info.profile().sector()).isEqualTo("Technology");
        assertThat(info.profile().industry()).isEqualTo("Consumer Electronics");
        assertThat(info.profile().website()).isEqualTo(URI.create("https://www.apple.com"));
        assertThat(info.profile().fullTimeEmployees()).isEqualTo(161000);
        assertThat(info.profile().officers()).singleElement()
                .satisfies(o -> assertThat(o.name()).isEqualTo("Mr. Timothy D. Cook"));

        var quote = info.quote();
        assertThat(quote.longName()).isEqualTo("Apple Inc.");
        assertThat(quote.currency()).isEqualTo(java.util.Currency.getInstance("USD"));
        assertThat(quote.price().regularMarketPrice()).isEqualByComparingTo("190.5");
        assertThat(quote.price().marketCap()).isEqualByComparingTo("2950000000000");
        assertThat(quote.price().volume()).isEqualTo(52_000_000L);
        assertThat(quote.keyStats().trailingPe()).isEqualByComparingTo("31.2");
        assertThat(quote.keyStats().trailingEps()).isEqualByComparingTo("6.13");
        assertThat(quote.analyst().recommendationKey()).isEqualTo("buy");
        assertThat(quote.analyst().targetMeanPrice()).isEqualByComparingTo("205.0");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/v10/finance/quoteSummary/AAPL");
        assertThat(req.getRequestUrl().queryParameter("modules")).contains("financialData", "assetProfile");
        assertThat(req.getRequestUrl().queryParameter("formatted")).isEqualTo("false");
    }

    @Test
    void parsesRecommendationsUpgradesCalendarAndFilings() {
        server.enqueue(Fixtures.jsonResponse("quotesummary_aapl.json"));

        var info = service.getInfo(Symbol.of("AAPL"));

        assertThat(info.recommendationTrend()).hasSize(2);
        assertThat(info.recommendationTrend().getFirst().strongBuy()).isEqualTo(11);

        assertThat(info.upgradesDowngrades()).singleElement().satisfies(u -> {
            assertThat(u.firm()).isEqualTo("Morgan Stanley");
            assertThat(u.toGrade()).isEqualTo("Overweight");
            assertThat(u.gradeDate()).isEqualTo(Instant.ofEpochSecond(1706832000));
        });

        assertThat(info.earningsDates()).containsExactly(
                Instant.ofEpochSecond(1714752000), Instant.ofEpochSecond(1715011200));

        assertThat(info.secFilings()).singleElement().satisfies(f -> {
            assertThat(f.type()).isEqualTo("10-Q");
            assertThat(f.url()).isEqualTo(URI.create("https://www.sec.gov/x.htm"));
        });
    }

    @Test
    void throwsOnErrorEnvelope() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"Quote not found\"}}}"));

        assertThatThrownBy(() -> service.getInfo(Symbol.of("NOPE")))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("Quote not found");
    }

    // ---- /v7/finance/quote fallback for symbols quoteSummary has no data for (indices, ETFs, crypto)

    @Test
    void fallsBackToQuoteEndpointWhenQuoteSummaryHasNoFundamentals() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json")
                .setBody("{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\","
                        + "\"description\":\"No fundamentals data found for symbol: ^GSPC\"}}}"));
        server.enqueue(Fixtures.jsonResponse("quote_gspc_btc.json"));

        var info = service.getInfo(Symbol.of("^GSPC"));

        assertThat(server.takeRequest().getPath()).startsWith("/v10/finance/quoteSummary/%5EGSPC");
        RecordedRequest fallback = server.takeRequest();
        assertThat(fallback.getPath()).startsWith("/v7/finance/quote");
        assertThat(fallback.getRequestUrl().queryParameter("symbols")).isEqualTo("^GSPC");

        assertThat(info.profile()).isNull();
        assertThat(info.recommendationTrend()).isEmpty();
        assertThat(info.secFilings()).isEmpty();
        var quote = info.quote();
        assertThat(quote.symbol()).isEqualTo(Symbol.of("^GSPC"));
        assertThat(quote.quoteType()).isEqualTo("INDEX");
        assertThat(quote.shortName()).isEqualTo("S&P 500");
        assertThat(quote.exchange()).isEqualTo("SNP");
        assertThat(quote.currency()).isEqualTo(java.util.Currency.getInstance("USD"));
        assertThat(quote.price().regularMarketPrice()).isEqualByComparingTo("7706.96");
        assertThat(quote.price().previousClose()).isEqualByComparingTo("7704.13");
        assertThat(quote.price().volume()).isEqualTo(561_979_122L);
        assertThat(quote.price().marketCap()).isNull();
        assertThat(quote.keyStats().trailingPe()).isNull();
        assertThat(quote.analyst().recommendationMean()).isNull();
    }

    @Test
    void fallsBackOnErrorEnvelopeToo() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\","
                        + "\"description\":\"No fundamentals data found for symbol: BTC-USD\"}}}"));
        server.enqueue(Fixtures.jsonResponse("quote_gspc_btc.json"));

        var info = service.getInfo(Symbol.of("BTC-USD"));

        assertThat(info.quote().symbol()).isEqualTo(Symbol.of("BTC-USD"));
        assertThat(info.quote().quoteType()).isEqualTo("CRYPTOCURRENCY");
    }

    @Test
    void rethrowsOriginalErrorWhenFallbackHasNoResult() {
        server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json")
                .setBody("{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\","
                        + "\"description\":\"Quote not found for ticker symbol: NOPE\"}}}"));
        server.enqueue(Fixtures.jsonResponse("quote_empty.json"));

        assertThatThrownBy(() -> service.getInfo(Symbol.of("NOPE")))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("Quote not found for ticker symbol: NOPE");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void doesNotFallBackOnRateLimit() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("slow down"));

        assertThatThrownBy(() -> service.getInfo(Symbol.of("AAPL")))
                .isInstanceOf(io.ziggy.yfinance.exception.YFRateLimitException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void singleQuoteMapsEquityFieldsWithConsistentUnits() throws Exception {
        server.enqueue(Fixtures.jsonResponse("quote_aapl.json"));

        var quote = service.getQuote(Symbol.of("AAPL"));

        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols")).isEqualTo("AAPL");
        assertThat(quote.longName()).isEqualTo("Apple Inc.");
        assertThat(quote.exchange()).isEqualTo("NMS");
        assertThat(quote.marketState()).isEqualTo("REGULAR");
        assertThat(quote.price().regularMarketPrice()).isEqualByComparingTo("336.62");
        assertThat(quote.price().open()).isEqualByComparingTo("336.04");
        assertThat(quote.price().dayLow()).isEqualByComparingTo("334.53");
        assertThat(quote.price().dayHigh()).isEqualByComparingTo("337.47");
        assertThat(quote.price().fiftyTwoWeekLow()).isEqualByComparingTo("243.42");
        assertThat(quote.price().marketCap()).isEqualByComparingTo("4912692854784");
        assertThat(quote.keyStats().trailingPe()).isEqualByComparingTo("38.558994");
        assertThat(quote.keyStats().trailingEps()).isEqualByComparingTo("8.73");
        assertThat(quote.keyStats().forwardEps()).isEqualByComparingTo("9.58535");
        assertThat(quote.keyStats().bookValue()).isEqualByComparingTo("7.36");
        assertThat(quote.keyStats().priceToBook()).isEqualByComparingTo("45.736412");
        assertThat(quote.keyStats().sharesOutstanding()).isEqualTo(14_594_180_000L);
        assertThat(quote.keyStats().beta()).isNull(); // not served by /v7/finance/quote
        // v7 "dividendYield" is a percentage (0.32); quoteSummary's is a fraction. Use the fraction.
        assertThat(quote.keyStats().dividendYield()).isEqualByComparingTo("0.003125744");
        // "2.2 - Buy" -> mean 2.2, key "buy" (same vocabulary as quoteSummary's recommendationKey)
        assertThat(quote.analyst().recommendationMean()).isEqualByComparingTo("2.2");
        assertThat(quote.analyst().recommendationKey()).isEqualTo("buy");
        assertThat(quote.analyst().targetMeanPrice()).isNull();
    }

    @Test
    void singleQuoteThrowsWhenSymbolUnknown() {
        server.enqueue(Fixtures.jsonResponse("quote_empty.json"));

        assertThatThrownBy(() -> service.getQuote(Symbol.of("NOPE")))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("NOPE");
    }

    @Test
    void batchQuotesUseOneRequestKeepOrderAndOmitUnknownSymbols() throws Exception {
        server.enqueue(Fixtures.jsonResponse("quote_gspc_btc.json"));

        var quotes = service.getQuotes(List.of(Symbol.of("^GSPC"), Symbol.of("NOPE"), Symbol.of("BTC-USD")));

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(server.takeRequest().getRequestUrl().queryParameter("symbols")).isEqualTo("^GSPC,NOPE,BTC-USD");
        assertThat(quotes.keySet()).containsExactly(Symbol.of("^GSPC"), Symbol.of("BTC-USD"));
        assertThat(quotes.get(Symbol.of("BTC-USD")).quoteType()).isEqualTo("CRYPTOCURRENCY");
    }

    @Test
    void batchQuotesWithNoSymbolsMakesNoRequest() {
        assertThat(service.getQuotes(List.of())).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }
}
