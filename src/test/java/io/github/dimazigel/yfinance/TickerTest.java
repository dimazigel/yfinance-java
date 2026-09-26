package io.github.dimazigel.yfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.exception.YFClassMismatchException;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Crypto;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.FxPair;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.instrument.Unclassified;
import io.github.dimazigel.yfinance.service.HistoryRequest;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.Instruments;
import io.github.dimazigel.yfinance.testsupport.YahooDispatcher;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.time.LocalDate;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TickerTest {

    private MockWebServer server;
    private YFinance yf;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        server.setDispatcher(new YahooDispatcher());
        yf = YFinance.fromApis(Fixtures.apis(server));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void instrumentIsTypedByAssetClass() {
        var ticker = yf.ticker("aapl");
        assertThat(ticker.symbol()).isEqualTo(Symbol.of("AAPL"));

        Instrument instrument = ticker.instrument();

        assertThat(instrument).isInstanceOfSatisfying(Equity.class, e -> {
            assertThat(e.symbol()).isEqualTo(Symbol.of("AAPL"));
            assertThat(e.valuation().marketCap()).isPositive();
        });
        assertThat(yf.ticker("EURUSD=X").instrument()).isInstanceOf(FxPair.class);
        assertThat(server.getRequestCount()).as("one v7 request per instrument() call").isEqualTo(2);
    }

    @Test
    void instrumentThrowsForAnUnknownSymbol() {
        assertThatThrownBy(() -> yf.ticker(YahooDispatcher.UNKNOWN).instrument())
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining(YahooDispatcher.UNKNOWN);
    }

    @Test
    void asReturnsTheInstrumentWhenTheClassMatches() {
        Equity equity = yf.ticker("AAPL").as(Equity.class);

        assertThat(equity.symbol()).isEqualTo(Symbol.of("AAPL"));
        assertThat(equity.assetClass()).isEqualTo(AssetClass.EQUITY);
        assertThat(yf.ticker("AAPL").as(Instrument.class)).isInstanceOf(Equity.class);
    }

    @Test
    void asThrowsClassMismatchWithTheActualClass() {
        assertThatThrownBy(() -> yf.ticker("AAPL").as(Etf.class))
                .isInstanceOf(YFClassMismatchException.class)
                .isInstanceOf(YFDataException.class)
                .hasMessage("AAPL is EQUITY, not Etf")
                .satisfies(e -> {
                    var mismatch = (YFClassMismatchException) e;
                    assertThat(mismatch.actual()).isEqualTo(AssetClass.EQUITY);
                    assertThat(mismatch.requested()).isEqualTo(Etf.class);
                });
    }

    @Test
    void asOnADowngradedInstrumentReportsUnclassified() {
        // BAC-PL is a preferred share Yahoo reports as EQUITY but without a market cap
        assertThat(yf.ticker("BAC-PL").instrument()).isInstanceOf(Unclassified.class);

        assertThatThrownBy(() -> yf.ticker("BAC-PL").as(Equity.class))
                .isInstanceOf(YFClassMismatchException.class)
                .hasMessage("BAC-PL is UNCLASSIFIED, not Equity")
                .satisfies(e -> assertThat(((YFClassMismatchException) e).actual()).isEqualTo(AssetClass.UNCLASSIFIED));
    }

    @Test
    void detailReturnsTheClassDetailForEveryDetailClass() {
        var aapl = yf.ticker("AAPL");
        assertThat(aapl.detail(aapl.as(Equity.class)).profile().sector()).isEqualTo("Technology");

        var spy = yf.ticker("SPY");
        assertThat(spy.detail(spy.as(Etf.class)).symbol()).isEqualTo(Symbol.of("SPY"));

        var vfiax = yf.ticker("VFIAX");
        assertThat(vfiax.detail(vfiax.as(MutualFund.class)).symbol()).isEqualTo(Symbol.of("VFIAX"));

        var btc = yf.ticker("BTC-USD");
        assertThat(btc.detail(btc.as(Crypto.class)).name()).isEqualTo("Bitcoin");
    }

    @Test
    void detailRejectsAProofForAnotherSymbol() {
        Equity aapl = yf.ticker("AAPL").as(Equity.class);
        Etf spy = yf.ticker("SPY").as(Etf.class);
        MutualFund vfiax = yf.ticker("VFIAX").as(MutualFund.class);
        Crypto btc = yf.ticker("BTC-USD").as(Crypto.class);
        int requestsSoFar = server.getRequestCount();

        var msft = yf.ticker("MSFT");
        assertThatThrownBy(() -> msft.detail(aapl)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAPL").hasMessageContaining("MSFT");
        assertThatThrownBy(() -> msft.detail(spy)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SPY").hasMessageContaining("MSFT");
        assertThatThrownBy(() -> msft.detail(vfiax)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VFIAX").hasMessageContaining("MSFT");
        assertThatThrownBy(() -> msft.detail(btc)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BTC-USD").hasMessageContaining("MSFT");
        assertThat(server.getRequestCount()).as("rejected before any request").isEqualTo(requestsSoFar);
    }

    @Test
    void detailThrowsWhenQuoteSummaryNoLongerKnowsTheSymbol() {
        Equity gone = Instruments.withSymbol(Instruments.equity("AAPL"), "GONE");

        assertThatThrownBy(() -> yf.ticker("GONE").detail(gone))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("GONE")
                .hasMessageContaining("UNKNOWN_SYMBOL");
    }

    @Test
    void optionsIsEmptyForAnInstrumentWithoutListedOptions() {
        assertThat(yf.ticker("EURUSD=X").options()).isEmpty();
        assertThat(yf.ticker("AAPL").options()).hasValueSatisfying(chain -> assertThat(chain.calls()).hasSize(36));
    }

    @Test
    void optionsForAnExpirationSendsTheDate() throws Exception {
        Instant expiration = Instant.ofEpochSecond(1790726400);

        var chain = yf.ticker("AAPL").options(expiration).orElseThrow();

        assertThat(chain.expiration()).isEqualTo(expiration);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getRequestUrl().queryParameter("date")).isEqualTo("1790726400");
    }

    @Test
    void statementsUsesTheEquityProof() {
        var ticker = yf.ticker("AAPL");
        Equity equity = ticker.as(Equity.class);

        var income = ticker.statements(equity, StatementType.INCOME, Frequency.ANNUAL);

        assertThat(income.type()).isEqualTo(StatementType.INCOME);
        assertThat(income.value("TotalRevenue", LocalDate.parse("2023-09-30")).orElseThrow())
                .isEqualByComparingTo("383285000000");
        assertThat(income.value("NetIncome", LocalDate.parse("2022-09-30"))).isEmpty();
    }

    @Test
    void statementsRejectsMismatchedEquityProof() {
        var ticker = yf.ticker("AAPL");

        assertThatThrownBy(() -> ticker.statements(Instruments.equity("MSFT"), StatementType.INCOME, Frequency.ANNUAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MSFT")
                .hasMessageContaining("AAPL");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void historyOverloadsAndDividendsAndSplits() throws Exception {
        var ticker = yf.ticker("AAPL");

        assertThat(ticker.history(Range.ONE_MONTH, Interval.ONE_DAY).bars()).hasSize(3);
        var monthly = server.takeRequest().getRequestUrl();
        assertThat(monthly.queryParameter("range")).isEqualTo("1mo");
        assertThat(monthly.queryParameter("interval")).isEqualTo("1d");

        ticker.history(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000), Interval.ONE_DAY);
        var window = server.takeRequest().getRequestUrl();
        assertThat(window.queryParameter("period1")).isEqualTo("1000");
        assertThat(window.queryParameter("period2")).isEqualTo("2000");

        assertThat(ticker.dividends()).singleElement().satisfies(d -> assertThat(d.amount()).isEqualByComparingTo("0.24"));
        assertThat(server.takeRequest().getRequestUrl().queryParameter("range")).isEqualTo("max");
        assertThat(ticker.splits()).singleElement().satisfies(s -> assertThat(s.ratio()).isEqualTo("4:1"));
    }

    @Test
    void historyRejectsARequestBuiltForAnotherSymbol() {
        var request = HistoryRequest.builder(Symbol.of("MSFT")).range(Range.ONE_MONTH).build();

        assertThatThrownBy(() -> yf.ticker("AAPL").history(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MSFT")
                .hasMessageContaining("AAPL");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void newsSearchesBySymbol() throws Exception {
        var news = yf.ticker("AAPL").news();

        assertThat(news).singleElement().satisfies(n -> {
            assertThat(n.title()).contains("Apple announces new product");
            assertThat(n.link()).isPresent();
        });
        assertThat(server.takeRequest().getRequestUrl().queryParameter("q")).isEqualTo("AAPL");
    }
}
