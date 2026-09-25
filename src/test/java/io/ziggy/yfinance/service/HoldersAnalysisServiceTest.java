package io.ziggy.yfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.ziggy.yfinance.api.QuoteApi;
import io.ziggy.yfinance.api.QuoteSummaryApi;
import io.ziggy.yfinance.testsupport.Fixtures;
import io.ziggy.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.time.LocalDate;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HoldersAnalysisServiceTest {

    private MockWebServer server;
    private QuoteService quoteService;
    private HoldersService holdersService;
    private AnalysisService analysisService;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        quoteService = new QuoteService(Fixtures.api(server, QuoteSummaryApi.class), Fixtures.api(server, QuoteApi.class));
        holdersService = new HoldersService(quoteService);
        analysisService = new AnalysisService(quoteService);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void parsesHolders() {
        server.enqueue(Fixtures.jsonResponse("quotesummary_holders_aapl.json"));

        var holders = holdersService.getHolders(Symbol.of("AAPL"));

        assertThat(holders.breakdown().institutionsPercentHeld()).isEqualByComparingTo("0.6125");
        assertThat(holders.breakdown().institutionsCount()).isEqualTo(6543);

        assertThat(holders.institutional()).singleElement().satisfies(h -> {
            assertThat(h.organization()).isEqualTo("Vanguard Group Inc");
            assertThat(h.pctHeld()).isEqualByComparingTo("0.0843");
            assertThat(h.reportDate()).isEqualTo(Instant.ofEpochSecond(1703980800));
        });

        assertThat(holders.insiderTransactions()).singleElement().satisfies(t -> {
            assertThat(t.filerName()).isEqualTo("COOK TIMOTHY D");
            assertThat(t.shares()).isEqualTo(511000L);
        });
    }

    @Test
    void parsesInsiderRosterAndNetSharePurchaseActivity() {
        server.enqueue(Fixtures.jsonResponse("quotesummary_holders_aapl.json"));

        var holders = holdersService.getHolders(Symbol.of("AAPL"));

        assertThat(holders.insiderRoster()).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("COOK TIMOTHY D");
            assertThat(r.relation()).isEqualTo("Chief Executive Officer");
            assertThat(r.latestTransactionDescription()).isEqualTo("Sale");
            assertThat(r.latestTransactionDate()).isEqualTo(Instant.ofEpochSecond(1701302400));
            assertThat(r.positionDirect()).isEqualTo(3_280_000L);
        });

        var activity = holders.netSharePurchaseActivity();
        assertThat(activity.period()).isEqualTo("6m");
        assertThat(activity.buyShares()).isEqualTo(100_000L);
        assertThat(activity.sellShares()).isEqualTo(500_000L);
        assertThat(activity.netShares()).isEqualTo(-400_000L);
        assertThat(activity.netPercentInsiderShares()).isEqualByComparingTo("-0.1");
        assertThat(activity.totalInsiderShares()).isEqualTo(4_000_000L);
    }

    @Test
    void parsesEarningsHistoryEpsTrendRevisionsAndGrowth() {
        var estimates = enqueueAll(4);

        assertThat(analysisService.getEarningsHistory(Symbol.of("AAPL"))).hasSize(2)
                .first().satisfies(h -> {
                    assertThat(h.period()).isEqualTo("-1q");
                    assertThat(h.quarter()).isEqualTo(Instant.ofEpochSecond(1703980800));
                    assertThat(h.epsActual()).isEqualByComparingTo("2.18");
                    assertThat(h.epsEstimate()).isEqualByComparingTo("2.1");
                    assertThat(h.surprisePercent()).isEqualByComparingTo("0.038");
                });

        assertThat(analysisService.getEpsTrend(Symbol.of("AAPL"))).hasSize(2)
                .first().satisfies(t -> {
                    assertThat(t.period()).isEqualTo("0q");
                    assertThat(t.current()).isEqualByComparingTo("1.5");
                    assertThat(t.sevenDaysAgo()).isEqualByComparingTo("1.49");
                    assertThat(t.ninetyDaysAgo()).isEqualByComparingTo("1.42");
                });

        assertThat(analysisService.getEpsRevisions(Symbol.of("AAPL"))).hasSize(2)
                .first().satisfies(r -> {
                    assertThat(r.upLast7Days()).isEqualTo(3);
                    assertThat(r.upLast30Days()).isEqualTo(8);
                    assertThat(r.downLast30Days()).isEqualTo(1);
                });

        assertThat(analysisService.getGrowthEstimates(Symbol.of("AAPL"))).hasSize(2)
                .first().satisfies(g -> {
                    assertThat(g.period()).isEqualTo("0q");
                    assertThat(g.growth()).isEqualByComparingTo("0.045");
                });
    }

    private int enqueueAll(int times) {
        for (int i = 0; i < times; i++) {
            server.enqueue(Fixtures.jsonResponse("quotesummary_holders_aapl.json"));
        }
        return times;
    }

    @Test
    void parsesAnalystPriceTargets() {
        server.enqueue(Fixtures.jsonResponse("quotesummary_holders_aapl.json"));

        var target = analysisService.getAnalystPriceTargets(Symbol.of("AAPL"));

        assertThat(target.current()).isEqualByComparingTo("190.5");
        assertThat(target.low()).isEqualByComparingTo("158.0");
        assertThat(target.high()).isEqualByComparingTo("250.0");
        assertThat(target.mean()).isEqualByComparingTo("205.0");
        assertThat(target.numberOfAnalysts()).isEqualTo(38);
    }

    @Test
    void parsesEarningsEstimates() {
        server.enqueue(Fixtures.jsonResponse("quotesummary_holders_aapl.json"));

        var estimates = analysisService.getEarningsEstimate(Symbol.of("AAPL"));

        assertThat(estimates).hasSize(2);
        var first = estimates.getFirst();
        assertThat(first.period()).isEqualTo("0q");
        assertThat(first.endDate()).isEqualTo(LocalDate.parse("2024-03-31"));
        assertThat(first.average()).isEqualByComparingTo("1.5");
        assertThat(first.numberOfAnalysts()).isEqualTo(27);
    }
}
