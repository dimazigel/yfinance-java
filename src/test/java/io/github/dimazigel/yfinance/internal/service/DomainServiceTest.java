package io.github.dimazigel.yfinance.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.enums.SectorKey;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFMissingDataException;
import io.github.dimazigel.yfinance.internal.api.DomainApi;
import io.github.dimazigel.yfinance.sector.Industry;
import io.github.dimazigel.yfinance.sector.Sector;
import io.github.dimazigel.yfinance.testsupport.Fixtures;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
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

class DomainServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private MockWebServer server;
    private DomainService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        service = new DomainService(Fixtures.api(server, DomainApi.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void parsesASectorFromTheRealFixture() throws Exception {
        server.enqueue(Fixtures.jsonResponse("domain/sector_technology.json"));

        Sector tech = service.getSector(SectorKey.TECHNOLOGY);

        assertThat(tech.key()).isEqualTo(SectorKey.TECHNOLOGY);
        assertThat(tech.name()).isEqualTo("Technology");
        assertThat(tech.symbol()).isEqualTo(Symbol.of("^YH311"));
        assertThat(tech.industriesCount()).isEqualTo(12);
        assertThat(tech.fetchedAt()).isEqualTo(NOW);
        assertThat(tech.overview().companiesCount()).isEqualTo(861);
        assertThat(tech.overview().marketCap()).isEqualByComparingTo("29851801092096");
        assertThat(tech.overview().marketWeight()).as("a fraction").isEqualByComparingTo("0.33951327");
        assertThat(tech.overview().employeeCount()).isEqualTo(7955784L);
        assertThat(tech.overview().messageBoardId()).isEqualTo("INDEXYH311");
        assertThat(tech.overview().description()).startsWith("Companies engaged in the design");
        assertThat(tech.performance().dayChange()).isEqualByComparingTo("0.0064176237");
        assertThat(tech.performance().ytd()).isEqualByComparingTo("0.28099045");
        assertThat(tech.performance().oneYear()).isEqualByComparingTo("0.26850027");
        assertThat(tech.performance().threeYear()).isEqualByComparingTo("1.3921548");
        assertThat(tech.performance().fiveYear()).isEqualByComparingTo("1.3663447");
        assertThat(tech.benchmark().name()).isEqualTo("S&P 500");
        assertThat(tech.benchmark().performance().ytd()).isEqualByComparingTo("0.109523416");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().encodedPath()).isEqualTo("/v1/finance/sectors/technology");
        assertThat(req.getRequestUrl().queryParameter("withReturns")).isEqualTo("true");
    }

    @Test
    void sectorListsKeepYahoosOrderAndLeaveAbsentOptionalsEmpty() {
        server.enqueue(Fixtures.jsonResponse("domain/sector_technology.json"));

        Sector tech = service.getSector(SectorKey.TECHNOLOGY);

        assertThat(tech.topCompanies()).hasSize(50);
        var nvda = tech.topCompanies().getFirst();
        assertThat(nvda.symbol()).isEqualTo(Symbol.of("NVDA"));
        assertThat(nvda.name()).contains("NVIDIA Corporation");
        assertThat(nvda.lastPrice()).isEqualByComparingTo("228.38");
        assertThat(nvda.marketCap()).isEqualByComparingTo("5514691977905");
        assertThat(nvda.marketWeight()).isEqualByComparingTo("0.17822886");
        assertThat(nvda.dayChangePercent()).isEqualByComparingTo("0.00514941");
        assertThat(nvda.ytdReturn().orElseThrow()).isEqualByComparingTo("0.2246");
        assertThat(nvda.rating()).contains("Strong Buy");
        assertThat(nvda.targetPrice().orElseThrow()).isEqualByComparingTo("327.7");
        var skHynix = tech.topCompanies().stream().filter(c -> c.symbol().value().equals("SKHY")).findFirst().orElseThrow();
        assertThat(skHynix.rating()).isEmpty();
        assertThat(skHynix.targetPrice()).isEmpty();
        assertThat(skHynix.ytdReturn()).isEmpty();

        assertThat(tech.topEtfs()).hasSize(10);
        var qqq = tech.topEtfs().getFirst();
        assertThat(qqq.symbol()).isEqualTo(Symbol.of("QQQ"));
        assertThat(qqq.name()).contains("Invesco QQQ Trust, Series 1");
        assertThat(qqq.lastPrice()).isEqualByComparingTo("739.77");
        assertThat(qqq.netAssets()).isEqualByComparingTo("488981000838");
        assertThat(qqq.expenseRatio()).as("a fraction").isEqualByComparingTo("0.0018");
        assertThat(qqq.ytdReturn().orElseThrow()).isEqualByComparingTo("0.2042");

        assertThat(tech.topMutualFunds()).hasSize(10);
        assertThat(tech.topMutualFunds().getFirst().symbol()).isEqualTo(Symbol.of("VITAX"));
        assertThat(tech.topMutualFunds()).filteredOn(f -> f.symbol().value().equals("FFOJX"))
                .singleElement().satisfies(f -> assertThat(f.name()).as("null on the wire").isEmpty());

        // Yahoo's list opens with an "All Industries" total that has no key: it is not an industry.
        assertThat(tech.industries()).hasSize(12).hasSize(tech.industriesCount());
        var semis = tech.industries().getFirst();
        assertThat(semis.key()).isEqualTo("semiconductors");
        assertThat(semis.name()).isEqualTo("Semiconductors");
        assertThat(semis.symbol()).isEqualTo(Symbol.of("^YH31130020"));
        assertThat(semis.marketWeight()).isEqualByComparingTo("0.38464093");
        assertThat(semis.ytdReturn()).isEqualByComparingTo("0.45074663");

        assertThat(tech.researchReports()).hasSize(4);
        var jabil = tech.researchReports().getFirst();
        assertThat(jabil.id()).isEqualTo("ARGUS_3100_AnalystReport_1790851953000");
        assertThat(jabil.title()).isEqualTo("Analyst Report: Jabil Inc");
        assertThat(jabil.provider()).isEqualTo("Argus Research");
        assertThat(jabil.type()).isEqualTo("Analyst Report");
        assertThat(jabil.published()).isEqualTo(Instant.parse("2026-10-01T10:52:33Z"));
        assertThat(jabil.summary()).startsWith("St. Petersburg, Florida-based Jabil Inc.");
        assertThat(jabil.investmentRating()).contains("Bullish");
        assertThat(jabil.targetPrice().orElseThrow()).isEqualByComparingTo("475.0");
        assertThat(jabil.targetPriceStatus()).contains("Maintained");
        var digest = tech.researchReports().get(1);
        assertThat(digest.type()).isEqualTo("Market Summary");
        assertThat(digest.investmentRating()).isEmpty();
        assertThat(digest.targetPrice()).isEmpty();
    }

    @Test
    void parsesAnIndustryFromTheRealFixture() throws Exception {
        server.enqueue(Fixtures.jsonResponse("domain/industry_semiconductors.json"));

        Industry semis = service.getIndustry("semiconductors");

        assertThat(semis.key()).isEqualTo("semiconductors");
        assertThat(semis.name()).isEqualTo("Semiconductors");
        assertThat(semis.symbol()).isEqualTo(Symbol.of("^YH31130020"));
        assertThat(semis.sectorKey()).isEqualTo("technology");
        assertThat(semis.sector()).contains(SectorKey.TECHNOLOGY);
        assertThat(semis.sectorName()).isEqualTo("Technology");
        assertThat(semis.fetchedAt()).isEqualTo(NOW);
        assertThat(semis.overview().companiesCount()).isEqualTo(60);
        assertThat(semis.overview().marketWeight()).as("its weight within the sector").isEqualByComparingTo("0.38464093");
        assertThat(semis.performance().fiveYear()).isEqualByComparingTo("5.124406");
        assertThat(semis.benchmark().name()).isEqualTo("S&P 500");

        assertThat(semis.topCompanies()).hasSize(49);
        assertThat(semis.topCompanies().getFirst().symbol()).isEqualTo(Symbol.of("NVDA"));
        assertThat(semis.topCompanies()).filteredOn(c -> c.symbol().value().equals("AMBQ"))
                .singleElement().satisfies(c -> assertThat(c.name()).as("null on the wire").isEmpty());

        assertThat(semis.topPerformingCompanies()).hasSize(5);
        var maxLinear = semis.topPerformingCompanies().getFirst();
        assertThat(maxLinear.symbol()).isEqualTo(Symbol.of("MXL"));
        assertThat(maxLinear.name()).contains("MaxLinear, Inc.");
        assertThat(maxLinear.lastPrice()).isEqualByComparingTo("90.31");
        assertThat(maxLinear.ytdReturn().orElseThrow()).isEqualByComparingTo("4.1813");
        assertThat(maxLinear.targetPrice().orElseThrow()).isEqualByComparingTo("94.5454");

        assertThat(semis.topGrowthCompanies()).hasSize(5);
        var micron = semis.topGrowthCompanies().getFirst();
        assertThat(micron.symbol()).isEqualTo(Symbol.of("MU"));
        assertThat(micron.growthEstimate().orElseThrow()).isEqualByComparingTo("5.846153846153847");
        assertThat(semis.researchReports()).hasSize(4);

        assertThat(server.takeRequest().getRequestUrl().encodedPath()).isEqualTo("/v1/finance/industries/semiconductors");
    }

    @Test
    void theIndustryKeyIsTrimmedAndLowerCased() throws Exception {
        server.enqueue(Fixtures.jsonResponse("domain/industry_banks_diversified.json"));

        Industry banks = service.getIndustry("  Banks-Diversified ");

        assertThat(banks.key()).isEqualTo("banks-diversified");
        assertThat(banks.sector()).contains(SectorKey.FINANCIAL_SERVICES);
        assertThat(server.takeRequest().getRequestUrl().encodedPath()).isEqualTo("/v1/finance/industries/banks-diversified");
    }

    @Test
    void anIndustryYahooDoesNotKnowIsMissingData() {
        server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type", "text/html")
                .setBody(Fixtures.load("domain/industry_not_found.html")));

        assertThatThrownBy(() -> service.getIndustry("no-such-industry"))
                .isInstanceOfSatisfying(YFMissingDataException.class, e -> {
                    assertThat(e.subject()).isEqualTo("no-such-industry");
                    assertThat(e.field()).isEqualTo("industry");
                    assertThat(e.isRetryable()).isFalse();
                })
                .hasMessageContaining("no-such-industry");
    }

    @Test
    void aBlankIndustryKeyIsRejectedBeforeAnyRequest() {
        assertThatThrownBy(() -> service.getIndustry("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void aSectorWithoutItsOverviewIsADataError() {
        server.enqueue(json("{\"data\":{\"name\":\"Technology\",\"symbol\":\"^YH311\",\"key\":\"technology\"}}"));

        assertThatThrownBy(() -> service.getSector(SectorKey.TECHNOLOGY))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("technology")
                .hasMessageContaining("overview");
    }

    @Test
    void rowsMissingARequiredFieldAreDroppedAndLogged() {
        server.enqueue(json(Fixtures.load("domain/industry_banks_diversified.json")
                .replaceFirst("\"symbol\"\\s*:\\s*\"JPM\"", "\"symbol\":null")));

        try (var log = LogCapture.ofLibrary()) {
            Industry banks = service.getIndustry("banks-diversified");

            assertThat(banks.topCompanies()).hasSize(9).noneMatch(c -> c.name().orElse("").startsWith("JPMorgan"));
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m)
                    .isEqualTo("Dropped 1 of 10 topCompanies rows without a complete required field"));
        }
    }

    @Test
    void requestsRunInsideALogContextScope() {
        var seen = new HashMap<String, String>();
        var api = Fixtures.apis(server, chain -> {
            seen.put(chain.request().url().encodedPath(), String.valueOf(MDC.get("yf.op")));
            return chain.proceed(chain.request());
        }).domain();
        server.enqueue(Fixtures.jsonResponse("domain/sector_technology.json"));
        server.enqueue(Fixtures.jsonResponse("domain/industry_semiconductors.json"));
        var scoped = new DomainService(api, Clock.systemUTC());

        scoped.getSector(SectorKey.TECHNOLOGY);
        scoped.getIndustry("semiconductors");

        assertThat(seen).containsEntry("/v1/finance/sectors/technology", "sector")
                .containsEntry("/v1/finance/industries/semiconductors", "industry");
        assertThat(MDC.get("yf.op")).isNull();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }
}
