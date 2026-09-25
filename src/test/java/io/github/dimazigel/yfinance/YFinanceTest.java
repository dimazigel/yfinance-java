package io.github.dimazigel.yfinance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.api.YahooApis;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.LookupType;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import java.time.LocalDate;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YFinanceTest {

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
    void tickerDividendsAndSplitsConveniences() throws Exception {
        var ticker = yf.ticker("AAPL");

        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));
        var dividends = ticker.dividends();
        assertThat(dividends).singleElement()
                .satisfies(d -> assertThat(d.amount()).isEqualByComparingTo("0.24"));
        assertThat(server.takeRequest().getRequestUrl().queryParameter("range")).isEqualTo("max");

        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));
        assertThat(ticker.splits()).singleElement()
                .satisfies(s -> assertThat(s.ratio()).isEqualTo("4:1"));
    }

    @Test
    void tickerNewsSearchesBySymbol() throws Exception {
        server.enqueue(Fixtures.jsonResponse("search_apple.json"));

        var news = yf.ticker("AAPL").news();

        assertThat(news).singleElement()
                .satisfies(n -> assertThat(n.title()).isEqualTo("Apple announces new product"));
        assertThat(server.takeRequest().getRequestUrl().queryParameter("q")).isEqualTo("AAPL");
    }

    @Test
    void tickerExposesExtendedAnalysis() {
        var ticker = yf.ticker("AAPL");
        for (int i = 0; i < 4; i++) {
            server.enqueue(Fixtures.jsonResponse("quotesummary_holders_aapl.json"));
        }

        assertThat(ticker.earningsHistory()).hasSize(2);
        assertThat(ticker.epsTrend()).hasSize(2);
        assertThat(ticker.epsRevisions()).hasSize(2);
        assertThat(ticker.growthEstimates()).hasSize(2);
    }

    @Test
    void tickerExposesHistoryInfoFundamentalsAndOptions() {
        var ticker = yf.ticker("aapl");
        assertThat(ticker.symbol().value()).isEqualTo("AAPL");

        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));
        var history = ticker.history(Range.ONE_MONTH, Interval.ONE_DAY);
        assertThat(history.bars()).hasSize(3);

        server.enqueue(Fixtures.jsonResponse("quotesummary_aapl.json"));
        assertThat(ticker.info().profile().sector()).isEqualTo("Technology");

        server.enqueue(Fixtures.jsonResponse("timeseries_income_annual.json"));
        var income = ticker.financials(StatementType.INCOME, Frequency.ANNUAL);
        assertThat(income.value("TotalRevenue", LocalDate.parse("2023-09-30")))
                .isEqualByComparingTo("383285000000");

        server.enqueue(Fixtures.jsonResponse("options_aapl.json"));
        assertThat(ticker.optionChain().calls()).hasSize(1);
    }

    @Test
    void historyBackfillOverloadSendsPeriodParams() throws Exception {
        server.enqueue(Fixtures.jsonResponse("chart_aapl_1d.json"));

        yf.ticker("AAPL").history(
                java.time.Instant.ofEpochSecond(1000),
                java.time.Instant.ofEpochSecond(2000),
                Interval.ONE_DAY);

        var url = server.takeRequest().getRequestUrl();
        assertThat(url.queryParameter("period1")).isEqualTo("1000");
        assertThat(url.queryParameter("period2")).isEqualTo("2000");
        assertThat(url.queryParameter("interval")).isEqualTo("1d");
    }

    @Test
    void yFinanceIsCloseable() {
        YFinance closeable = YFinance.fromApis(
                io.github.dimazigel.yfinance.api.YahooApis.create(
                        Fixtures.retrofit(server.url("/")), Fixtures.retrofit(server.url("/"))));
        closeable.close(); // no-op for fromApis, must not throw
        closeable.close(); // idempotent
    }

    @Test
    void facadeExposesSearchAndLookup() {
        server.enqueue(Fixtures.jsonResponse("search_apple.json"));
        assertThat(yf.search("apple").quotes()).hasSize(2);

        server.enqueue(Fixtures.jsonResponse("lookup_apple.json"));
        assertThat(yf.lookup("apple", LookupType.EQUITY)).hasSize(2);
    }

    @Test
    void tickersFanOutAcrossSymbols() {
        var tickers = yf.tickers("AAPL", "MSFT");
        assertThat(tickers.symbols()).extracting(s -> s.value()).containsExactly("AAPL", "MSFT");

        server.enqueue(Fixtures.jsonResponse("quotesummary_aapl.json"));
        server.enqueue(Fixtures.jsonResponse("quotesummary_aapl.json"));
        var infos = tickers.infos();
        assertThat(infos).hasSize(2);
        assertThat(infos.get(tickers.symbols().getFirst()).orElseThrow().profile().sector())
                .isEqualTo("Technology");
    }

    @Test
    void tickerQuoteAndFacadeBatchQuotesUseTheQuoteEndpoint() throws Exception {
        server.enqueue(Fixtures.jsonResponse("quote_aapl.json"));
        var quote = yf.ticker("AAPL").quote();
        assertThat(quote.price().regularMarketPrice()).isEqualByComparingTo("336.62");
        assertThat(server.takeRequest().getPath()).startsWith("/v7/finance/quote");

        server.enqueue(Fixtures.jsonResponse("quote_gspc_btc.json"));
        var quotes = yf.quotes("^GSPC", "BTC-USD");
        assertThat(quotes.keySet()).extracting(s -> s.value()).containsExactly("^GSPC", "BTC-USD");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void tickerRejectsHistoryRequestBuiltForAnotherSymbol() {
        var request = io.github.dimazigel.yfinance.service.HistoryRequest.builder(io.github.dimazigel.yfinance.valueobject.Symbol.of("MSFT"))
                .range(Range.ONE_MONTH).build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> yf.ticker("AAPL").history(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MSFT")
                .hasMessageContaining("AAPL");
        assertThat(server.getRequestCount()).isZero();
    }
}
