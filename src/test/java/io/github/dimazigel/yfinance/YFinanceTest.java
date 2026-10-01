package io.github.dimazigel.yfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.enums.Frequency;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.LineItem;
import io.github.dimazigel.yfinance.enums.LookupType;
import io.github.dimazigel.yfinance.enums.PredefinedScreen;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.enums.SectorKey;
import io.github.dimazigel.yfinance.enums.StatementType;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFMissingDataException;
import io.github.dimazigel.yfinance.exception.YFSkippedException;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Crypto;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.market.HistoryQuery;
import io.github.dimazigel.yfinance.screener.EquityScreenField;
import io.github.dimazigel.yfinance.screener.FundScreenField;
import io.github.dimazigel.yfinance.screener.ScreenOptions;
import io.github.dimazigel.yfinance.screener.ScreenQuery;
import io.github.dimazigel.yfinance.screener.ScreenResult;
import io.github.dimazigel.yfinance.sector.Industry;
import io.github.dimazigel.yfinance.sector.Sector;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.Instruments;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.testsupport.YahooDispatcher;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
        server.setDispatcher(new YahooDispatcher());
        yf = YFinance.fromApis(Fixtures.apis(server));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void instrumentsClassifiesEveryClassInOneRequest() {
        var batch = yf.instruments(symbols("AAPL", "SPY", "VFIAX", "^GSPC", "BTC-USD", "EURUSD=X", "ES=F", YahooDispatcher.UNKNOWN));

        assertThat(batch.size()).isEqualTo(8);
        assertThat(batch.values()).extracting(Instrument::assetClass).containsExactly(AssetClass.EQUITY, AssetClass.ETF,
                AssetClass.MUTUAL_FUND, AssetClass.INDEX, AssetClass.CRYPTO, AssetClass.FX, AssetClass.FUTURE);
        assertThat(batch.skipped()).singleElement().satisfies(s -> {
            assertThat(s.symbol()).isEqualTo(Symbol.of(YahooDispatcher.UNKNOWN));
            assertThat(s.reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL);
        });
        assertThat(server.getRequestCount()).as("one batched v7 request, no fallback").isEqualTo(1);
    }

    @Test
    void sectorAndIndustryPagesAreOneRequestEach() {
        Sector tech = yf.sector(SectorKey.TECHNOLOGY);
        Industry semis = yf.industry(tech.industries().getFirst().key());

        assertThat(tech.name()).isEqualTo("Technology");
        assertThat(tech.topCompanies()).hasSize(50);
        assertThat(semis.name()).isEqualTo("Semiconductors");
        assertThat(semis.sector()).contains(SectorKey.TECHNOLOGY);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void anUnknownIndustryIsMissingData() {
        assertThatThrownBy(() -> yf.industry("no-such-industry"))
                .isInstanceOf(YFMissingDataException.class)
                .hasMessageContaining("no-such-industry");
    }

    @Test
    void screensReturnTypedInstrumentsInOneRequest() {
        ScreenResult gainers = yf.screen(PredefinedScreen.DAY_GAINERS);
        ScreenResult funds = yf.screen(PredefinedScreen.TOP_MUTUAL_FUNDS, ScreenOptions.defaults().withSize(5));
        ScreenResult largeTech = yf.screenEquities(ScreenQuery.and(
                ScreenQuery.eq(EquityScreenField.SECTOR, "Technology"),
                ScreenQuery.gt(EquityScreenField.INTRADAYMARKETCAP, 100_000_000_000L)));
        ScreenResult ratedFunds = yf.screenFunds(ScreenQuery.gte(FundScreenField.PERFORMANCERATINGOVERALL, 4));

        assertThat(gainers.screen()).map(ScreenResult.Info::title).contains("Day Gainers");
        assertThat(gainers.instruments().values()).hasSize(5).allSatisfy(i -> assertThat(i).isInstanceOf(Equity.class));
        assertThat(funds.instruments().values()).isNotEmpty().allSatisfy(i -> assertThat(i.assetClass()).isEqualTo(AssetClass.MUTUAL_FUND));
        assertThat(largeTech.total()).isEqualTo(57);
        assertThat(largeTech.instruments().values().getFirst().symbol()).isEqualTo(Symbol.of("NVDA"));
        assertThat(ratedFunds.instruments().values()).isNotEmpty();
        assertThat(server.getRequestCount()).as("one request per screen").isEqualTo(4);
    }

    @Test
    void typedInstrumentsSkipOtherClasses() {
        var equities = yf.instruments(symbols("AAPL", "SPY"), Equity.class);

        assertThat(equities.values()).singleElement().satisfies(e -> assertThat(e.symbol()).isEqualTo(Symbol.of("AAPL")));
        assertThat(equities.skipped()).singleElement().satisfies(s -> assertThat(s.reason()).isEqualTo(SkipReason.WRONG_ASSET_CLASS));
    }

    @Test
    void detailsForEveryDetailClass() {
        Equity aapl = yf.ticker("AAPL").as(Equity.class);
        Etf spy = yf.ticker("SPY").as(Etf.class);
        MutualFund vfiax = yf.ticker("VFIAX").as(MutualFund.class);
        Crypto btc = yf.ticker("BTC-USD").as(Crypto.class);

        assertThat(yf.equityDetails(List.of(aapl)).values()).singleElement()
                .satisfies(d -> assertThat(d.profile().sector()).isEqualTo("Technology"));
        assertThat(yf.etfDetails(List.of(spy)).values()).singleElement()
                .satisfies(d -> assertThat(d.symbol()).isEqualTo(Symbol.of("SPY")));
        assertThat(yf.mutualFundDetails(List.of(vfiax)).values()).singleElement()
                .satisfies(d -> assertThat(d.symbol()).isEqualTo(Symbol.of("VFIAX")));
        assertThat(yf.cryptoDetails(List.of(btc)).values()).singleElement()
                .satisfies(d -> assertThat(d.name()).isEqualTo("Bitcoin"));
    }

    @Test
    void detailByInstrumentIsOneRequestPerCall() {   // batch B, item 2
        Equity aapl = Instruments.equity("AAPL");          // built from fixtures: no request
        Etf spy = yf.ticker("SPY").as(Etf.class);
        MutualFund vfiax = yf.ticker("VFIAX").as(MutualFund.class);
        Crypto btc = yf.ticker("BTC-USD").as(Crypto.class);
        int before = server.getRequestCount();

        assertThat(yf.detail(aapl).profile().sector()).isEqualTo("Technology");
        assertThat(server.getRequestCount()).as("detail(Equity): one quoteSummary request").isEqualTo(before + 1);
        assertThat(yf.detail(spy).symbol()).isEqualTo(Symbol.of("SPY"));
        assertThat(server.getRequestCount()).as("detail(Etf)").isEqualTo(before + 2);
        assertThat(yf.detail(vfiax).symbol()).isEqualTo(Symbol.of("VFIAX"));
        assertThat(server.getRequestCount()).as("detail(MutualFund)").isEqualTo(before + 3);
        assertThat(yf.detail(btc).name()).isEqualTo("Bitcoin");
        assertThat(server.getRequestCount()).as("detail(Crypto)").isEqualTo(before + 4);
    }

    @Test
    void detailByInstrumentThrowsSkippedForAVanishedSymbol() {   // batch B, item 2
        Equity gone = Instruments.equity("GONE");

        assertThatThrownBy(() -> yf.detail(gone))
                .isInstanceOf(YFSkippedException.class)
                .satisfies(e -> assertThat(((YFSkippedException) e).reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL));
    }

    @Test
    void statementsByInstrumentIsOneRequest() throws Exception {   // batch B, item 2
        Equity aapl = Instruments.equity("AAPL");

        var income = yf.statements(aapl, StatementType.INCOME, Frequency.ANNUAL);

        assertThat(income.type()).isEqualTo(StatementType.INCOME);
        assertThat(income.value("TotalRevenue", LocalDate.parse("2023-09-30")).orElseThrow()).isEqualByComparingTo("383285000000");
        assertThat(server.getRequestCount()).isEqualTo(1);
        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/ws/fundamentals-timeseries/v1/finance/timeseries/AAPL");
        assertThat(req.getRequestUrl().queryParameter("type")).contains("annualTotalRevenue");
    }

    @Test
    void detailsKeepOrderAndSkipVanishedSymbols() {
        Equity aapl = yf.ticker("AAPL").as(Equity.class);
        Equity gone = Instruments.withSymbol(aapl, "GONE");

        var batch = yf.equityDetails(List.of(gone, aapl));

        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(Symbol.of("GONE"), Symbol.of("AAPL"));
        assertThat(batch.outcomes().getFirst()).isInstanceOfSatisfying(Outcome.Skipped.class,
                s -> assertThat(s.reason()).isEqualTo(SkipReason.UNKNOWN_SYMBOL));
        assertThat(batch.values()).hasSize(1);
    }

    @Test
    void optionsBatchIsOkForBothChainsAndNoListedOptions() {
        var batch = yf.options(symbols("AAPL", "EURUSD=X"));

        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(Symbol.of("AAPL"), Symbol.of("EURUSD=X"));
        assertThat(batch.failed()).isEmpty();
        assertThat(batch.values()).hasSize(2);
        assertThat(batch.values().get(0)).hasValueSatisfying(chain -> assertThat(chain.calls()).hasSize(36));
        assertThat(batch.values().get(1)).isEmpty();
    }

    @Test
    void historiesPreserveInputOrderAndIsolateFailures() {
        var batch = yf.histories(symbols("MSFT", YahooDispatcher.UNKNOWN, "AAPL"), Range.ONE_MONTH, Interval.ONE_DAY);

        assertThat(batch.outcomes()).extracting(Outcome::symbol)
                .containsExactly(Symbol.of("MSFT"), Symbol.of(YahooDispatcher.UNKNOWN), Symbol.of("AAPL"));
        assertThat(batch.values()).hasSize(2).allSatisfy(h -> assertThat(h.bars()).hasSize(3));
        assertThat(batch.failed()).singleElement().satisfies(f -> {
            assertThat(f.symbol()).isEqualTo(Symbol.of(YahooDispatcher.UNKNOWN));
            assertThat(f.error()).isInstanceOf(YFDataException.class);
        });
        assertThat(batch.skipped()).isEmpty();
    }

    @Test
    void historiesWithQueryUsesAnExplicitWindowAndPreservesOrder() {
        var query = HistoryQuery.of(Interval.ONE_DAY)
                .period(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000))
                .build();

        var batch = yf.histories(symbols("MSFT", YahooDispatcher.UNKNOWN, "AAPL"), query);

        assertThat(batch.outcomes()).extracting(Outcome::symbol)
                .containsExactly(Symbol.of("MSFT"), Symbol.of(YahooDispatcher.UNKNOWN), Symbol.of("AAPL"));
        assertThat(batch.values()).hasSize(2).allSatisfy(h -> assertThat(h.bars()).hasSize(3));
        assertThat(batch.failed()).singleElement().satisfies(f -> assertThat(f.symbol()).isEqualTo(Symbol.of(YahooDispatcher.UNKNOWN)));
    }

    @Test
    void statementsBatchUsesEachEquityAsItsOwnProof() {
        Equity aapl = yf.ticker("AAPL").as(Equity.class);
        Equity msft = Instruments.withSymbol(aapl, "MSFT");

        var batch = yf.statements(List.of(aapl, msft, aapl), StatementType.INCOME, Frequency.ANNUAL);

        assertThat(batch.outcomes()).extracting(Outcome::symbol)
                .containsExactly(Symbol.of("AAPL"), Symbol.of("MSFT"), Symbol.of("AAPL"));
        assertThat(batch.values()).hasSize(3).allSatisfy(s ->
                assertThat(s.value("TotalRevenue", LocalDate.parse("2023-09-30")).orElseThrow()).isEqualByComparingTo("383285000000"));
    }

    @Test
    void multiStatementsBatchIsOneRequestPerEquity() {   // batch B, item 3; chunk count updated for batch E/1
        Equity aapl = yf.ticker("AAPL").as(Equity.class);
        Equity msft = Instruments.withSymbol(aapl, "MSFT");
        int before = server.getRequestCount();
        var types = java.util.Set.of(StatementType.INCOME, StatementType.CASH_FLOW);
        var frequencies = java.util.Set.of(Frequency.ANNUAL, Frequency.QUARTERLY);
        // FundamentalsService.MAX_KEYS_PER_REQUEST = 150: with the full upstream LineItem list this
        // pair no longer fits in one request per equity.
        int keysPerEquity = LineItem.forStatement(StatementType.INCOME).size() * 2
                + LineItem.forStatement(StatementType.CASH_FLOW).size() * 2;
        int chunksPerEquity = (keysPerEquity + 149) / 150;

        var batch = yf.statements(List.of(aapl, msft), types, frequencies);

        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(Symbol.of("AAPL"), Symbol.of("MSFT"));
        assertThat(server.getRequestCount()).isEqualTo(before + 2 * chunksPerEquity);
        assertThat(batch.values()).hasSize(2).allSatisfy(byType -> {
            assertThat(byType.keySet()).containsExactlyInAnyOrder(StatementType.INCOME, StatementType.CASH_FLOW);
            assertThat(byType.get(StatementType.INCOME).keySet()).containsExactlyInAnyOrder(Frequency.ANNUAL, Frequency.QUARTERLY);
            assertThat(byType.get(StatementType.INCOME).get(Frequency.ANNUAL).value("TotalRevenue", LocalDate.parse("2023-09-30")).orElseThrow())
                    .isEqualByComparingTo("383285000000");
            assertThat(byType.get(StatementType.CASH_FLOW).get(Frequency.QUARTERLY).value("OperatingCashFlow", LocalDate.parse("2024-06-30")).orElseThrow())
                    .isEqualByComparingTo("28858000000");
        });

        var single = yf.statements(aapl, types, frequencies);
        assertThat(server.getRequestCount()).as("the single form chunks the same way")
                .isEqualTo(before + 2 * chunksPerEquity + chunksPerEquity);
        assertThat(single.get(StatementType.CASH_FLOW).get(Frequency.ANNUAL).type()).isEqualTo(StatementType.CASH_FLOW);
    }

    @Test
    void batchStatementsRejectInvalidArgumentsBeforeAnyRequest() {   // review of batch B, Important 1
        Equity aapl = Instruments.equity("AAPL");
        Equity msft = Instruments.withSymbol(aapl, "MSFT");
        var equities = List.of(aapl, msft);

        assertThatThrownBy(() -> yf.statements(equities, java.util.Set.of(), java.util.Set.of(Frequency.ANNUAL)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("types");
        assertThatThrownBy(() -> yf.statements(equities, java.util.Set.of(StatementType.INCOME), java.util.Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("frequencies");
        assertThatThrownBy(() -> yf.statements(equities, java.util.Set.of(StatementType.BALANCE_SHEET), java.util.Set.of(Frequency.TRAILING)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trailing").hasMessageContaining("balance sheet");
        assertThatThrownBy(() -> yf.statements(equities, StatementType.BALANCE_SHEET, Frequency.TRAILING))
                .as("the single-statement batch form shares the guard")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trailing").hasMessageContaining("balance sheet");
        assertThat(server.getRequestCount()).as("a programming error never reaches the fan-out").isZero();
    }

    @Test
    void emptyInputsMakeNoRequest() {
        assertThat(yf.instruments(List.of()).size()).isZero();
        assertThat(yf.instruments(List.of(), Equity.class).size()).isZero();
        assertThat(yf.equityDetails(List.of()).size()).isZero();
        assertThat(yf.histories(List.of(), Range.ONE_MONTH, Interval.ONE_DAY).size()).isZero();
        assertThat(yf.statements(List.of(), StatementType.INCOME, Frequency.ANNUAL).size()).isZero();
        assertThat(yf.statements(List.of(), java.util.Set.of(StatementType.INCOME), java.util.Set.of(Frequency.ANNUAL)).size()).isZero();
        assertThat(yf.options(List.of()).size()).isZero();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void tickersAreBuiltFromStringsOrSymbols() {
        assertThat(yf.tickers("aapl", "MSFT").symbols()).containsExactly(Symbol.of("AAPL"), Symbol.of("MSFT"));
        assertThat(yf.tickers(List.of(Symbol.of("SPY"))).symbols()).containsExactly(Symbol.of("SPY"));
        assertThat(yf.ticker(Symbol.of("AAPL")).symbol()).isEqualTo(Symbol.of("AAPL"));
    }

    @Test
    void facadeExposesSearchAndLookup() {
        var result = yf.search("apple");
        assertThat(result.quotes()).hasSize(2);
        assertThat(result.quotes().getFirst().longName()).contains("Apple Inc.");

        var quotes = yf.lookup("apple", LookupType.EQUITY);
        assertThat(quotes).hasSize(2);
        assertThat(quotes.getFirst().regularMarketPrice()).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("190.5"));
    }

    @Test
    void yFinanceIsCloseable() {
        YFinance closeable = YFinance.fromApis(Fixtures.apis(server));
        closeable.close(); // no-op for fromApis, must not throw
        closeable.close(); // idempotent
    }

    @Test
    void createLogsEffectiveConfigAndCloseLogsOnce() {
        var config = EndpointConfig.production()
                .withHosts(server.url("/"))
                .withCallTimeout(Duration.ofSeconds(7));

        try (var log = LogCapture.of(YFinance.class)) {
            YFinance created = YFinance.create(config); // no request yet: the crumb handshake is lazy
            created.close();

            assertThat(log.messages(Level.INFO)).singleElement().satisfies(m -> assertThat(m)
                    .startsWith("yfinance-java client created:")
                    .contains("callTimeout=PT7S")
                    .contains("rateLimit=on/3 attempts")
                    .contains("retry5xx=3 attempts")
                    .contains("customizer=no"));
            assertThat(log.messages(Level.DEBUG)).containsExactly("yfinance-java client closed");
        }
    }

    @Test
    void bothClientsShareOneSizedDispatcherAndPoolWhichCloseShutsDown() {
        // A builder's build() shares the builder's dispatcher and pool, so a customizer can observe
        // what each of the two clients was given without the facade exposing them.
        var built = new java.util.ArrayList<okhttp3.OkHttpClient>();
        var config = EndpointConfig.production().withHosts(server.url("/"))
                .withFanOutConcurrency(12)
                .withClientCustomizer(b -> built.add(b.build()));

        var created = YFinance.create(config);
        assertThat(built).hasSize(2);
        okhttp3.Dispatcher dispatcher = built.get(0).dispatcher();
        okhttp3.ConnectionPool pool = built.get(0).connectionPool();
        assertThat(built.get(1).dispatcher()).as("handshake and api client share the dispatcher").isSameAs(dispatcher);
        assertThat(built.get(1).connectionPool()).as("...and the connection pool").isSameAs(pool);
        assertThat(dispatcher.getMaxRequestsPerHost()).as("sized by fanOutConcurrency for enqueue()d calls through the customizer").isEqualTo(12);
        assertThat(dispatcher.executorService().isShutdown()).isFalse();

        created.close();
        assertThat(dispatcher.executorService().isShutdown()).as("the library created it, so close() shuts it down").isTrue();
    }

    @Test
    void closeLeavesACustomizerSuppliedDispatcherAlone() {   // robustness review, item 8
        var mine = new okhttp3.Dispatcher();
        var config = EndpointConfig.production().withHosts(server.url("/")).withClientCustomizer(b -> b.dispatcher(mine));

        YFinance.create(config).close();

        assertThat(mine.executorService().isShutdown()).as("a shared dispatcher is the caller's to close").isFalse();
        mine.executorService().shutdown();
    }

    @Test
    void fanOutConcurrencyFromTheConfigBoundsDetailRequestsAndSeedsTickers() {   // final review, finding 5
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();
        server.setDispatcher(new YahooDispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (!request.getRequestUrl().encodedPath().startsWith("/v10/finance/quoteSummary/")) {
                    return super.dispatch(request);
                }
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlight.decrementAndGet();
                }
                return super.dispatch(request);
            }
        });
        Equity aapl = Instruments.equity("AAPL");
        Equity plug = Instruments.equity("PLUG");
        var config = EndpointConfig.production().withHosts(server.url("/"));

        try (var serial = YFinance.create(config.withFanOutConcurrency(1))) {
            var batch = serial.equityDetails(List.of(aapl, plug, aapl, plug));
            assertThat(batch.values()).hasSize(4);
            assertThat(maxInFlight.get()).as("one quoteSummary request at a time").isEqualTo(1);
            assertThat(serial.tickers("AAPL", "PLUG").concurrency()).as("Tickers default follows the config").isEqualTo(1);
            assertThat(serial.tickers("AAPL").withConcurrency(3).concurrency()).as("per-instance override still wins").isEqualTo(3);
        }

        maxInFlight.set(0);
        try (var parallel = YFinance.create(config.withFanOutConcurrency(4))) {
            var batch = parallel.equityDetails(List.of(aapl, plug, aapl, plug));
            assertThat(batch.values()).hasSize(4);
            assertThat(maxInFlight.get()).as("four permits: the requests overlap").isGreaterThan(1);
            assertThat(parallel.tickers("AAPL").concurrency()).isEqualTo(4);
        }
    }

    @Test
    void aCallerSuppliedCookieJarReceivesYahoosCookies() throws Exception {   // batch B, item 5
        server.setDispatcher(new YahooDispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return super.dispatch(request).addHeader("Set-Cookie", "A3=session-token; Path=/");
            }
        });
        var jar = new io.github.dimazigel.yfinance.http.InMemoryCookieJar();
        var config = EndpointConfig.production().withHosts(server.url("/")).withCookieJar(jar);

        try (var created = YFinance.create(config)) {
            created.ticker("AAPL").instrument();
        }

        assertThat(jar.loadForRequest(server.url("/"))).extracting(okhttp3.Cookie::name).contains("A3");
        assertThat(jar.loadForRequest(server.url("/"))).filteredOn(c -> c.name().equals("A3"))
                .singleElement().satisfies(c -> assertThat(c.value()).isEqualTo("session-token"));
    }

    @Test
    void aConfiguredClockStampsFetchedAtAndTimeseriesPeriod2() throws Exception {   // batch B, item 5
        var fixed = java.time.Clock.fixed(java.time.Instant.ofEpochSecond(1_750_000_000L), java.time.ZoneOffset.UTC);
        var config = EndpointConfig.production().withHosts(server.url("/")).withClock(fixed);

        try (var created = YFinance.create(config)) {
            Equity aapl = created.ticker("AAPL").as(Equity.class);
            assertThat(aapl.fetchedAt()).isEqualTo(fixed.instant());
            assertThat(created.detail(aapl).fetchedAt()).isEqualTo(fixed.instant());
            assertThat(created.statements(aapl, StatementType.INCOME, Frequency.ANNUAL).type()).isEqualTo(StatementType.INCOME);
        }

        RecordedRequest last = null;
        for (int i = 0; i < server.getRequestCount(); i++) {
            var req = server.takeRequest();
            if (req.getRequestUrl().encodedPath().startsWith("/ws/fundamentals-timeseries/")) {
                last = req;
            }
        }
        assertThat(last).isNotNull();
        assertThat(last.getRequestUrl().queryParameter("period2")).isEqualTo("1750000000");
    }

    @Test
    void sharesOutstandingDefaultWindowIsFiveFortyEightDaysFromTheConfiguredClock() throws Exception {   // batch E/1, item 4
        var fixed = java.time.Clock.fixed(java.time.Instant.ofEpochSecond(1_750_000_000L), java.time.ZoneOffset.UTC);
        var config = EndpointConfig.production().withHosts(server.url("/")).withClock(fixed);

        try (var created = YFinance.create(config)) {
            Equity aapl = created.ticker("AAPL").as(Equity.class);

            var points = created.sharesOutstanding(aapl);

            assertThat(points).hasSize(63);
        }

        RecordedRequest sharesRequest = null;
        for (int i = 0; i < server.getRequestCount(); i++) {
            var req = server.takeRequest();
            if ("shares_out".equals(req.getRequestUrl().queryParameter("type"))) {
                sharesRequest = req;
            }
        }
        assertThat(sharesRequest).isNotNull();
        assertThat(sharesRequest.getRequestUrl().queryParameter("period2")).isEqualTo("1750000000");
        assertThat(sharesRequest.getRequestUrl().queryParameter("period1")).isEqualTo(
                String.valueOf(1_750_000_000L - 548L * 86400));
    }

    @Test
    void sharesOutstandingExplicitWindowDelegatesToFundamentalsService() {   // batch E/1, item 4
        Equity aapl = yf.ticker("AAPL").as(Equity.class);

        var points = yf.sharesOutstanding(aapl, java.time.Instant.EPOCH, java.time.Instant.ofEpochSecond(2_000_000_000L));

        assertThat(points).hasSize(63);
        assertThatThrownBy(() -> yf.sharesOutstanding(aapl, java.time.Instant.EPOCH, java.time.Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Symbol> symbols(String... values) {
        return java.util.Arrays.stream(values).map(Symbol::of).toList();
    }
}
