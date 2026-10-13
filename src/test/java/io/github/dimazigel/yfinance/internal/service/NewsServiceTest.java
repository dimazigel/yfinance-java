package io.github.dimazigel.yfinance.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.enums.NewsTab;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.internal.api.NewsApi;
import io.github.dimazigel.yfinance.internal.dto.news.NewsRequest;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NewsServiceTest {

    private static final Symbol AAPL = Symbol.of("AAPL");
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
        server.enqueue(Fixtures.jsonResponse("news/gateway_news_AAPL.json"));

        var items = service.getNews(AAPL, NewsTab.NEWS, 10);

        assertThat(items).hasSize(10);
        NewsItem first = items.getFirst();
        assertThat(first.id()).isEqualTo("1ef88f61-ac08-42a8-8b69-24acf4cdec95");
        assertThat(first.title()).isEqualTo("Which AI stocks' valuations are outrunning reality?");
        assertThat(first.published()).isEqualTo(Instant.parse("2026-10-12T17:46:39Z"));
        assertThat(first.url()).isEqualTo(URI.create("https://finance.yahoo.com/video/which-ai-stocks-valuations-are-outrunning-reality-174639413.html"));
        assertThat(first.provider().name()).isEqualTo("Yahoo Finance Video");
        assertThat(first.provider().url()).contains(URI.create("https://finance.yahoo.com/"));
        assertThat(first.contentType()).contains("VIDEO");
        assertThat(first.summary()).hasValueSatisfying(s -> assertThat(s).startsWith("Market Hang's panel discusses "));
        assertThat(first.clickThroughUrl()).contains(URI.create("https://finance.yahoo.com/video/which-ai-stocks-valuations-are-outrunning-reality-174639413.html"));
        assertThat(first.thumbnail()).hasValueSatisfying(t -> {
            assertThat(t.url().toString()).startsWith("https://d29szjachogqwa.cloudfront.net/images/user-uploaded/video-frame");
            assertThat(t.width()).isEqualTo(1920);
            assertThat(t.height()).isEqualTo(1080);
        });
        assertThat(first.premium()).as("a video has no premium flag").isEmpty();
        assertThat(items.get(1).contentType()).contains("STORY");
        assertThat(items.get(1).premium()).contains(false);
    }

    @Test
    void keepsYahoosOrderAndLeavesAbsentOptionalsEmpty() {
        server.enqueue(Fixtures.jsonResponse("news/gateway_news_AAPL.json"));

        var items = service.getNews(AAPL, NewsTab.NEWS, 10);

        assertThat(items).extracting(NewsItem::id).containsExactlyElementsOf(fixtureIds("news/gateway_news_AAPL.json"));
        assertThat(items).extracting(NewsItem::contentType).containsOnly(java.util.Optional.of("STORY"), java.util.Optional.of("VIDEO"));
        assertThat(items).filteredOn(i -> i.contentType().orElseThrow().equals("VIDEO"))
                .as("a video carries no premium flag on the wire").isNotEmpty()
                .allSatisfy(i -> assertThat(i.premium()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gateway_news_AAPL", "gateway_all_AAPL", "gateway_press_AAPL", "gateway_news_SPY", "gateway_news_BTC_USD", "gateway_news_TTE_PA"})
    void everyCapturedItemCarriesTheRequiredFields(String fixture) {
        server.enqueue(Fixtures.jsonResponse("news/" + fixture + ".json"));

        assertThat(service.getNews(AAPL, NewsTab.NEWS, 10)).as("none of the captured items is dropped")
                .hasSameSizeAs(fixtureIds("news/" + fixture + ".json"));
    }

    @Test
    void postsTheTickerNewsQueryToTheGatewayRoot() throws Exception {
        server.enqueue(Fixtures.jsonResponse("news/gateway_news_AAPL.json"));

        service.getNews(AAPL, NewsTab.NEWS, 5);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/");
        assertThat(req.getRequestUrl().query()).isNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
        assertThat(req.getHeader(NewsApi.CLIENT_NAME_HEADER)).isEqualTo("finance");
        JsonNode body = JSON.readTree(req.getBody().readUtf8());
        assertThat(body.get("operationName").asString()).isEqualTo("TickerNews");
        assertThat(body.get("query").asString()).isEqualTo(NewsRequest.QUERY);
        JsonNode vars = body.get("variables");
        assertThat(vars.get("first").asInt()).isEqualTo(5);
        assertThat(vars.get("listInput").get("slug").asString()).isEqualTo("list=finance-US-en-US-ticker-news");
        assertThat(vars.get("listInput").get("queryVariables").get("tickerSymbol"))
                .isEqualTo(JSON.readTree("[\"AAPL\"]"));
        assertThat(vars.get("listInput").get("getFullList").asBoolean()).isTrue();
        assertThat(vars.get("mlRecsInput").get("rankInput").get("financeQuoteNewsV2").get("tickerSymbol"))
                .isEqualTo(JSON.readTree("[\"AAPL\"]"));
        assertThat(vars.get("clientContext"))
                .isEqualTo(JSON.readTree("{\"device\":\"desktop\",\"lang\":\"en-US\",\"region\":\"US\",\"site\":\"finance\"}"));
    }

    @ParameterizedTest
    @CsvSource({
        "ALL, list=finance-US-en-US-ticker-all",
        "NEWS, list=finance-US-en-US-ticker-news",
        "PRESS_RELEASES, list=finance-US-en-US-ticker-pressrelease"})
    void eachTabSendsItsListSlug(NewsTab tab, String slug) throws Exception {
        server.enqueue(Fixtures.jsonResponse("news/gateway_news_unknown.json"));

        service.getNews(AAPL, tab, 10);

        JsonNode body = JSON.readTree(server.takeRequest().getBody().readUtf8());
        assertThat(body.get("variables").get("listInput").get("slug").asString()).isEqualTo(slug);
    }

    @Test
    void aSymbolYahooHasNoNewsForIsAnEmptyList() {
        server.enqueue(Fixtures.jsonResponse("news/gateway_news_unknown.json"));

        assertThat(service.getNews(Symbol.of("ZZZZNOTREAL"), NewsTab.NEWS, 10)).isEmpty();
    }

    @Test
    void aResponseWithoutAStreamIsAnEmptyList() {
        server.enqueue(json("{\"data\":{\"lightyearList\":null}}"));

        assertThat(service.getNews(AAPL, NewsTab.NEWS, 10)).isEmpty();
    }

    @Test
    void graphqlErrorsWithoutAStreamAreADataException() {
        server.enqueue(json("{\"data\":{\"lightyearList\":null},\"errors\":[{\"message\":\"Error from networkapi_subgraph\","
                + "\"extensions\":{\"code\":\"BAD_USER_INPUT\"}}]}"));

        assertThatThrownBy(() -> service.getNews(AAPL, NewsTab.NEWS, 10))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("Error from networkapi_subgraph");
    }

    @Test
    void dropsAnItemMissingARequiredFieldAndLogsIt() {
        server.enqueue(stream(item("a1", "\"title\":\"Kept\",") + "," + item("a2", "")));

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
        server.enqueue(stream(item("a1", "\"title\":\"Kept\",") + "," + broken));

        assertThat(service.getNews(AAPL, NewsTab.NEWS, 10)).singleElement()
                .satisfies(i -> assertThat(i.id()).isEqualTo("a1"));
    }

    /** Past the entries it can hydrate (about fifty), Yahoo pads the stream with {@code AssetError} nodes. */
    @Test
    void dropsEntriesYahooCouldNotHydrateAndLogsThemSeparately() {
        server.enqueue(stream(item("a1", "\"title\":\"Kept\",") + ",{\"node\":{\"asset\":{\"__typename\":\"AssetError\"}}}"
                + ",{\"node\":{\"asset\":null}}"));

        try (var log = LogCapture.ofLibrary()) {
            var items = service.getNews(AAPL, NewsTab.NEWS, 10);

            assertThat(items).singleElement().satisfies(i -> assertThat(i.id()).isEqualTo("a1"));
            assertThat(log.messages(Level.DEBUG))
                    .anySatisfy(m -> assertThat(m).isEqualTo("Dropped 2 of 3 news entries Yahoo could not hydrate"))
                    .noneSatisfy(m -> assertThat(m).contains("without a complete required field"));
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
        server.enqueue(Fixtures.jsonResponse("news/gateway_news_unknown.json"));

        new NewsService(api).getNews(AAPL, NewsTab.NEWS, 10);

        assertThat(seen).containsEntry("yf.op", "news").containsEntry("yf.symbol", "AAPL");
        assertThat(MDC.get("yf.op")).isNull(); // cleared once the call returns
    }

    /** The ids of a fixture's entries, in Yahoo's order. */
    private static java.util.List<String> fixtureIds(String fixture) {
        JsonNode edges = JSON.readTree(Fixtures.load(fixture)).path("data").path("lightyearList").path("main").path("edges");
        var ids = new java.util.ArrayList<String>();
        for (JsonNode edge : edges) {
            ids.add(edge.path("node").path("asset").path("id").asString());
        }
        return ids;
    }

    /** A minimal stream entry in the wire shape; {@code titleField} is {@code "title":"…",} or empty to omit it. */
    private static String item(String id, String titleField) {
        return "{\"node\":{\"asset\":{\"__typename\":\"Story\",\"id\":\"" + id + "\"," + titleField
                + "\"contentAttributes\":{\"pubDate\":\"2026-09-30T13:57:00Z\","
                + "\"provider\":{\"displayName\":\"Reuters\"},"
                + "\"canonicalUrl\":\"https://example.com/" + id + "\"}}}}";
    }

    private static MockResponse stream(String edges) {
        return json("{\"data\":{\"lightyearList\":{\"main\":{\"edges\":[" + edges + "],\"totalCount\":9}}}}");
    }

    private static MockResponse json(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }
}
