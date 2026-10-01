package io.github.dimazigel.yfinance.internal.service;

import static io.github.dimazigel.yfinance.screener.EquityScreenField.INTRADAYMARKETCAP;
import static io.github.dimazigel.yfinance.screener.EquityScreenField.REGION;
import static io.github.dimazigel.yfinance.screener.EquityScreenField.SECTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.enums.PredefinedScreen;
import io.github.dimazigel.yfinance.exception.YFHttpException;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.instrument.Unclassified;
import io.github.dimazigel.yfinance.internal.api.QuoteApi;
import io.github.dimazigel.yfinance.internal.api.QuoteSummaryApi;
import io.github.dimazigel.yfinance.internal.api.ScreenerApi;
import io.github.dimazigel.yfinance.internal.http.RawQuoteClient;
import io.github.dimazigel.yfinance.screener.EquityScreenField;
import io.github.dimazigel.yfinance.screener.FundScreenField;
import io.github.dimazigel.yfinance.screener.ScreenOptions;
import io.github.dimazigel.yfinance.screener.ScreenQuery;
import io.github.dimazigel.yfinance.screener.ScreenResult;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.valueobject.Isin;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

class ScreenerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private MockWebServer server;
    private ScreenerService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        var quotes = new RawQuoteClient(Fixtures.api(server, QuoteApi.class), Fixtures.api(server, QuoteSummaryApi.class));
        service = new ScreenerService(Fixtures.api(server, ScreenerApi.class),
                new InstrumentService(quotes, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void aPredefinedScreenReturnsTypedInstrumentsFromItsRowsInOneRequest() throws Exception {
        server.enqueue(Fixtures.jsonResponse("screener/predefined_day_gainers.json"));

        ScreenResult result = service.screen(PredefinedScreen.DAY_GAINERS, ScreenOptions.defaults().withSize(5));

        assertThat(result.total()).isEqualTo(66);
        assertThat(result.offset()).isZero();
        assertThat(result.screen()).hasValueSatisfying(info -> {
            assertThat(info.title()).isEqualTo("Day Gainers");
            assertThat(info.canonicalName()).isEqualTo("DAY_GAINERS");
            assertThat(info.description()).startsWith("Discover the equities");
            assertThat(info.id()).isEqualTo("ec5bebb9-b7b2-4474-9e5c-3e258b61cbe6");
        });
        assertThat(result.instruments().outcomes()).extracting(Outcome::symbol).containsExactly(
                Symbol.of("ACN"), Symbol.of("EFXT"), Symbol.of("CTSH"), Symbol.of("EPAM"), Symbol.of("INFY"));
        assertThat(result.instruments().values()).as("the rows are full v7 rows: every one classifies")
                .hasSize(5).allSatisfy(i -> assertThat(i).isInstanceOf(Equity.class));
        assertThat(result.instruments().values().getFirst().fetchedAt()).isEqualTo(NOW);
        assertThat(server.getRequestCount()).as("no second request for the quotes, and no fallback").isEqualTo(1);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/v1/finance/screener/predefined/saved");
        assertThat(req.getRequestUrl().queryParameter("scrIds")).isEqualTo("day_gainers");
        assertThat(req.getRequestUrl().queryParameter("count")).isEqualTo("5");
        assertThat(req.getRequestUrl().queryParameter("start")).as("Yahoo ignores 'offset' on this endpoint").isEqualTo("0");
        assertThat(req.getRequestUrl().queryParameterNames()).doesNotContain("sortField", "sortType", "offset");
    }

    @Test
    void aFundScreenReturnsMutualFunds() {
        server.enqueue(Fixtures.jsonResponse("screener/predefined_top_mutual_funds.json"));

        ScreenResult result = service.screen(PredefinedScreen.TOP_MUTUAL_FUNDS, ScreenOptions.defaults());

        assertThat(result.total()).isEqualTo(1744);
        assertThat(result.instruments().values()).hasSize(5).allSatisfy(i -> assertThat(i).isInstanceOf(MutualFund.class));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void predefinedPagingAndSortingGoOnTheQueryString() throws Exception {
        server.enqueue(Fixtures.jsonResponse("screener/predefined_day_gainers.json"));

        service.screen(PredefinedScreen.DAY_GAINERS, ScreenOptions.defaults().withOffset(10).withSize(50).sortedBy(INTRADAYMARKETCAP, false));

        var url = server.takeRequest().getRequestUrl();
        assertThat(url.queryParameter("start")).isEqualTo("10");
        assertThat(url.queryParameter("count")).isEqualTo("50");
        assertThat(url.queryParameter("sortField")).isEqualTo("intradaymarketcap");
        assertThat(url.queryParameter("sortType")).isEqualTo("DESC");
    }

    @Test
    void aCustomEquityQueryIsPostedAsYahoosOperatorTree() throws Exception {
        server.enqueue(Fixtures.jsonResponse("screener/custom_equity.json"));
        var query = ScreenQuery.and(
                ScreenQuery.eq(REGION, "us"),
                ScreenQuery.eq(SECTOR, "Technology"),
                ScreenQuery.gt(INTRADAYMARKETCAP, 100_000_000_000L));

        ScreenResult result = service.screenEquities(query, ScreenOptions.defaults().withSize(5).sortedBy(INTRADAYMARKETCAP, false));

        assertThat(result.screen()).as("a custom query has no saved-screen metadata").isEmpty();
        assertThat(result.total()).isEqualTo(57);
        assertThat(result.instruments().values()).extracting(i -> i.symbol().value())
                .containsExactly("NVDA", "AAPL", "MSFT", "TSM", "AVGO");
        assertThat(result.instruments().values()).allSatisfy(i -> assertThat(i).isInstanceOf(Equity.class));
        assertThat(server.getRequestCount()).isEqualTo(1);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/v1/finance/screener");
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
        var json = JsonMapper.builder().build();
        assertThat(json.readTree(req.getBody().readUtf8())).isEqualTo(json.readTree("""
                {"offset":0,"size":5,"sortField":"intradaymarketcap","sortType":"DESC","quoteType":"EQUITY",
                 "userId":"","userIdType":"guid",
                 "query":{"operator":"AND","operands":[
                   {"operator":"EQ","operands":["region","us"]},
                   {"operator":"EQ","operands":["sector","Technology"]},
                   {"operator":"GT","operands":["intradaymarketcap",100000000000]}]}}"""));
    }

    @Test
    void aCustomQueryDefaultsToTickerDescendingAndNestsGroupsAndRanges() throws Exception {
        server.enqueue(Fixtures.jsonResponse("screener/custom_equity.json"));
        var query = ScreenQuery.or(
                ScreenQuery.between(INTRADAYMARKETCAP, 1e9, 2.5e9),
                ScreenQuery.isIn(SECTOR, "Technology", "Healthcare"));

        service.screenEquities(query, ScreenOptions.defaults().withOffset(25));

        var json = JsonMapper.builder().build();
        assertThat(json.readTree(server.takeRequest().getBody().readUtf8())).isEqualTo(json.readTree("""
                {"offset":25,"size":25,"sortField":"ticker","sortType":"DESC","quoteType":"EQUITY",
                 "userId":"","userIdType":"guid",
                 "query":{"operator":"OR","operands":[
                   {"operator":"BTWN","operands":["intradaymarketcap",1000000000,2500000000]},
                   {"operator":"OR","operands":[
                     {"operator":"EQ","operands":["sector","Technology"]},
                     {"operator":"EQ","operands":["sector","Healthcare"]}]}]}}"""));
    }

    @Test
    void aFundQueryIsPostedWithTheFundQuoteType() throws Exception {
        server.enqueue(Fixtures.jsonResponse("screener/predefined_top_mutual_funds.json"));

        service.screenFunds(ScreenQuery.gte(FundScreenField.PERFORMANCERATINGOVERALL, 4), ScreenOptions.defaults());

        var body = JsonMapper.builder().build().readTree(server.takeRequest().getBody().readUtf8());
        assertThat(body.path("quoteType").asString()).isEqualTo("MUTUALFUND");
        assertThat(body.path("query").path("operator").asString()).isEqualTo("GTE");
        assertThat(body.path("query").path("operands").get(0).asString()).isEqualTo("performanceratingoverall");
    }

    @Test
    void aRowShortOfARequiredFieldIsDowngradedNotRefetched() {
        // Apple's 15 listings: the thin regional ones come without market cap or share counts. A
        // screen is bulk, so it stays one request: no quoteSummary fallback per short row.
        server.enqueue(Fixtures.jsonResponse("screener/custom_isin_apple.json"));

        ScreenResult result = service.screenEquities(ScreenQuery.eq(EquityScreenField.ISIN, "US0378331005"),
                ScreenOptions.defaults().withSize(250));

        assertThat(server.getRequestCount()).as("exactly one request, whatever the rows lack").isEqualTo(1);
        assertThat(result.instruments().size()).isEqualTo(15);
        assertThat(result.instruments().failed()).isEmpty();
        assertThat(result.instruments().values()).filteredOn(i -> i instanceof Equity).hasSize(10);
        assertThat(result.instruments().values()).filteredOn(i -> i instanceof Unclassified).hasSize(5)
                .allSatisfy(i -> assertThat(((Unclassified) i).missing())
                        .containsExactly("marketCap", "sharesOutstanding", "impliedSharesOutstanding", "financialCurrency"));
    }

    @Test
    void listingsOfAnIsinAreOneRequestMostTradedFirst() throws Exception {
        server.enqueue(Fixtures.jsonResponse("screener/custom_isin_apple.json"));

        var listings = service.listings(Isin.of("US0378331005"));

        assertThat(listings.size()).isEqualTo(15);
        assertThat(listings.outcomes().getFirst().symbol()).as("the primary listing trades most").isEqualTo(Symbol.of("AAPL"));
        assertThat(listings.outcomes()).extracting(o -> o.symbol().value()).contains("APC.DE", "AAPL.MX", "0R2V.L");
        assertThat(server.getRequestCount()).isEqualTo(1);
        var json = JsonMapper.builder().build();
        assertThat(json.readTree(server.takeRequest().getBody().readUtf8())).isEqualTo(json.readTree("""
                {"offset":0,"size":250,"sortField":"dayvolume","sortType":"DESC","quoteType":"EQUITY",
                 "userId":"","userIdType":"guid",
                 "query":{"operator":"EQ","operands":["isin","US0378331005"]}}"""));
    }

    @Test
    void anIsinYahooHasNoEquityForIsAnEmptyBatch() {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("{\"finance\":{\"result\":[{\"start\":0,\"count\":0,\"total\":0,\"quotes\":[]}],\"error\":null}}"));

        assertThat(service.listings(Isin.of("US78462F1030")).size()).as("SPY is an ETF, not an equity").isZero();
    }

    @Test
    void yahoosErrorEnvelopeSurfacesWithItsDescription() {
        server.enqueue(new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                .setBody(Fixtures.load("screener/custom_invalid_field.json")));

        assertThatThrownBy(() -> service.screenEquities(ScreenQuery.eq(REGION, "us"), ScreenOptions.defaults()))
                .isInstanceOfSatisfying(YFHttpException.class, e -> assertThat(e.status()).isEqualTo(400))
                .hasMessageContaining("Unable to generate query");
    }

    @Test
    void anEmptyScreenIsAnEmptyBatch() {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("{\"finance\":{\"result\":[{\"start\":0,\"count\":0,\"total\":0,\"quotes\":[]}],\"error\":null}}"));

        ScreenResult result = service.screenEquities(ScreenQuery.eq(REGION, "us"), ScreenOptions.defaults());

        assertThat(result.total()).isZero();
        assertThat(result.instruments().size()).isZero();
    }

    @Test
    void requestsRunInsideALogContextScope() {
        var seen = new HashMap<String, String>();
        var apis = Fixtures.apis(server, chain -> {
            seen.put("op", String.valueOf(MDC.get("yf.op")));
            return chain.proceed(chain.request());
        });
        server.enqueue(Fixtures.jsonResponse("screener/predefined_day_gainers.json"));
        var scoped = new ScreenerService(apis.screener(),
                new InstrumentService(new RawQuoteClient(apis.quote(), apis.quoteSummary()), Clock.systemUTC()));

        scoped.screen(PredefinedScreen.DAY_GAINERS, ScreenOptions.defaults());

        assertThat(seen).containsEntry("op", "screen");
        assertThat(MDC.get("yf.op")).isNull();
    }
}
