package io.github.dimazigel.yfinance.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.enums.NewsTab;
import io.github.dimazigel.yfinance.internal.api.NewsApi;
import io.github.dimazigel.yfinance.news.NewsItem;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

class NewsServiceTest {

    private static final Symbol AAPL = Symbol.of("AAPL");

    private MockWebServer server;
    private NewsService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        service = new NewsService(Fixtures.api(server, NewsApi.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void parsesNewsFromTheRealFixture() {
        server.enqueue(Fixtures.jsonResponse("news/ncp_news_AAPL.json"));

        var items = service.getNews(AAPL, NewsTab.NEWS, 10);

        assertThat(items).hasSize(10);
        NewsItem first = items.getFirst();
        // Keys the survey found in 100% of 60 live items: non-null.
        assertThat(first.id()).isEqualTo("8f4828bf-8d97-4abf-b0bd-37602ea1a93c");
        assertThat(first.title()).isEqualTo("Apple's new CEO John Ternus plans on making some changes");
        assertThat(first.published()).isEqualTo(Instant.parse("2026-09-30T13:57:00Z"));
        assertThat(first.url()).isEqualTo(URI.create("https://finance.yahoo.com/video/apples-ceo-john-ternus-plans-135700100.html"));
        assertThat(first.provider().name()).isEqualTo("Yahoo Finance Video");
        assertThat(first.provider().url()).contains(URI.create("https://finance.yahoo.com/"));
        // Optional, but present on this item.
        assertThat(first.contentType()).contains("VIDEO");
        assertThat(first.summary()).hasValueSatisfying(s -> assertThat(s).startsWith("The 8:30 Hosts Julie Hyman"));
        assertThat(first.clickThroughUrl()).contains(first.url());
        assertThat(first.thumbnail()).hasValueSatisfying(t -> {
            assertThat(t.url().toString()).startsWith("https://s.yimg.com/uu/api/res/1.2/NjTv3WAdaK4EBp7a6oXwdg--");
            assertThat(t.width()).isEqualTo(7902);
            assertThat(t.height()).isEqualTo(5346);
        });
        assertThat(first.premium()).contains(false);
    }

    @Test
    void keepsYahoosOrderAndLeavesAbsentOptionalsEmpty() {
        server.enqueue(Fixtures.jsonResponse("news/ncp_news_AAPL.json"));

        var items = service.getNews(AAPL, NewsTab.NEWS, 10);

        // Yahoo's stream is not strictly newest-first (item 2 is newer than item 0); it is kept as served.
        assertThat(items.get(2).published()).isAfter(items.get(0).published());
        assertThat(items.get(2).contentType()).contains("STORY");
        assertThat(items.get(3).clickThroughUrl()).as("clickThroughUrl is null on the wire").isEmpty();
        assertThat(items.get(6).thumbnail()).as("thumbnail is null on the wire").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ncp_news_AAPL", "ncp_all_AAPL", "ncp_press_AAPL", "ncp_news_SPY", "ncp_news_BTC_USD", "ncp_news_TTE_PA"})
    void everyCapturedItemCarriesTheRequiredFields(String fixture) {
        server.enqueue(Fixtures.jsonResponse("news/" + fixture + ".json"));

        assertThat(service.getNews(AAPL, NewsTab.NEWS, 10)).as("none of the 10 captured items is dropped").hasSize(10);
    }

    @Test
    void postsTheSymbolAndCountToTheNcpEndpoint() throws Exception {
        server.enqueue(Fixtures.jsonResponse("news/ncp_news_AAPL.json"));

        service.getNews(AAPL, NewsTab.NEWS, 5);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/xhr/ncp");
        assertThat(req.getRequestUrl().queryParameter("queryRef")).isEqualTo("latestNews");
        assertThat(req.getRequestUrl().queryParameter("serviceKey")).isEqualTo("ncp_fin");
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
        var json = JsonMapper.builder().build();
        assertThat(json.readTree(req.getBody().readUtf8()))
                .isEqualTo(json.readTree("{\"serviceConfig\":{\"snippetCount\":5,\"s\":[\"AAPL\"]}}"));
    }

    @ParameterizedTest
    @CsvSource({"ALL, newsAll", "NEWS, latestNews", "PRESS_RELEASES, pressRelease"})
    void eachTabSendsItsQueryRef(NewsTab tab, String queryRef) throws Exception {
        server.enqueue(Fixtures.jsonResponse("news/ncp_news_unknown.json"));

        service.getNews(AAPL, tab, 10);

        assertThat(server.takeRequest().getRequestUrl().queryParameter("queryRef")).isEqualTo(queryRef);
    }

    @Test
    void aSymbolYahooHasNoNewsForIsAnEmptyList() {
        server.enqueue(Fixtures.jsonResponse("news/ncp_news_unknown.json"));

        assertThat(service.getNews(Symbol.of("ZZZZNOTREAL"), NewsTab.NEWS, 10)).isEmpty();
    }

    @Test
    void aResponseWithoutAStreamIsAnEmptyList() {
        server.enqueue(json("{\"status\":\"OK\"}"));

        assertThat(service.getNews(AAPL, NewsTab.NEWS, 10)).isEmpty();
    }

    @Test
    void dropsAnItemMissingARequiredFieldAndLogsIt() {
        server.enqueue(json("{\"data\":{\"tickerStream\":{\"stream\":["
                + item("a1", "\"title\":\"Kept\",") + "," + item("a2", "") + "]}}}"));

        try (var log = LogCapture.ofLibrary()) {
            var items = service.getNews(AAPL, NewsTab.NEWS, 10);

            assertThat(items).singleElement().satisfies(i -> assertThat(i.id()).isEqualTo("a1"));
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m)
                    .isEqualTo("Dropped 1 of 2 news items without a complete required field"));
        }
    }

    @Test
    void dropsAnItemWhosePublicationTimeCannotBeParsed() {
        String broken = item("a2", "\"title\":\"Broken\",").replace("2026-09-30T13:57:00Z", "yesterday");
        server.enqueue(json("{\"data\":{\"tickerStream\":{\"stream\":["
                + item("a1", "\"title\":\"Kept\",") + "," + broken + "]}}}"));

        assertThat(service.getNews(AAPL, NewsTab.NEWS, 10)).singleElement()
                .satisfies(i -> assertThat(i.id()).isEqualTo("a1"));
    }

    @Test
    void filtersOutAdvertisements() {
        String ad = item("ad1", "\"title\":\"Sponsored\",").replaceFirst("\\{", "{\"ad\":[{\"slot\":\"x\"}],");
        String notAnAd = item("a1", "\"title\":\"Kept\",").replaceFirst("\\{", "{\"ad\":[],");
        server.enqueue(json("{\"data\":{\"tickerStream\":{\"stream\":[" + ad + "," + notAnAd + "]}}}"));

        try (var log = LogCapture.ofLibrary()) {
            var items = service.getNews(AAPL, NewsTab.NEWS, 10);

            assertThat(items).singleElement().satisfies(i -> assertThat(i.id()).isEqualTo("a1"));
            assertThat(log.messages(Level.DEBUG)).as("an ad is not a dropped datum")
                    .noneSatisfy(m -> assertThat(m).startsWith("Dropped"));
        }
    }

    @Test
    void rejectsANonPositiveCountBeforeAnyRequest() {
        assertThatThrownBy(() -> service.getNews(AAPL, NewsTab.NEWS, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void requestsRunInsideALogContextScope() {
        var seen = new HashMap<String, String>();
        var api = Fixtures.apis(server, chain -> {
            seen.putAll(MDC.getCopyOfContextMap());
            return chain.proceed(chain.request());
        }).news();
        server.enqueue(Fixtures.jsonResponse("news/ncp_news_unknown.json"));

        new NewsService(api).getNews(AAPL, NewsTab.NEWS, 10);

        assertThat(seen).containsEntry("yf.op", "news").containsEntry("yf.symbol", "AAPL");
        assertThat(MDC.get("yf.op")).isNull(); // cleared once the call returns
    }

    /** A minimal stream entry in the wire shape; {@code titleField} is {@code "title":"…",} or empty to omit it. */
    private static String item(String id, String titleField) {
        return "{\"id\":\"" + id + "\",\"content\":{\"id\":\"" + id + "\"," + titleField
                + "\"pubDate\":\"2026-09-30T13:57:00Z\","
                + "\"provider\":{\"displayName\":\"Reuters\"},"
                + "\"canonicalUrl\":{\"url\":\"https://example.com/" + id + "\"}}}";
    }

    private static MockResponse json(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }
}
