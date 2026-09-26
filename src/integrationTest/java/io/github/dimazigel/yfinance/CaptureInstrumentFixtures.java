package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.http.YahooClientFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Refreshes the real-response fixtures under src/test/resources/fixtures/instruments. Run on demand:
 * {@code CAPTURE_FIXTURES=1 ./gradlew integrationTest --tests '*CaptureInstrumentFixtures*'}.
 * Paced through the library's client; never use raw curl against Yahoo.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "CAPTURE_FIXTURES", matches = "1")
class CaptureInstrumentFixtures {

    static final List<String> SYMBOLS = List.of("AAPL", "PLUG", "BAC-PL", "005930.KS", "TTE.PA", "SPY", "CSPX.L", "GLD",
            "VFIAX", "^GSPC", "BTC-USD", "EURUSD=X", "ES=F", "RIDE");
    static final String MODULES = "assetProfile,summaryProfile,summaryDetail,quoteType,price,financialData,defaultKeyStatistics,"
            + "calendarEvents,secFilings,recommendationTrend,upgradeDowngradeHistory,earningsTrend,earningsHistory,"
            + "majorHoldersBreakdown,institutionOwnership,fundOwnership,insiderHolders,insiderTransactions,"
            + "netSharePurchaseActivity,fundProfile,topHoldings,fundPerformance,esgScores";

    @Test
    void capture() throws Exception {
        var client = YahooClientFactory.apiClient(EndpointConfig.production());
        var out = Path.of("src/test/resources/fixtures/instruments");
        Files.createDirectories(out);
        for (String symbol : SYMBOLS) {
            String safe = symbol.replaceAll("[^A-Za-z0-9]", "_");
            var v7 = HttpUrl.get("https://query1.finance.yahoo.com/v7/finance/quote").newBuilder()
                    .addQueryParameter("symbols", symbol).addQueryParameter("formatted", "false").build();
            try (var resp = client.newCall(new Request.Builder().url(v7).build()).execute()) {
                Files.writeString(out.resolve("v7_" + safe + ".json"), resp.body().string());
            }
            var qs = HttpUrl.get("https://query1.finance.yahoo.com/v10/finance/quoteSummary/" + symbol).newBuilder()
                    .addQueryParameter("modules", MODULES).addQueryParameter("formatted", "false")
                    .addQueryParameter("corsDomain", "finance.yahoo.com").build();
            try (var resp = client.newCall(new Request.Builder().url(qs).build()).execute()) {
                Files.writeString(out.resolve("qs_" + safe + ".json"), resp.body().string()); // 404 bodies are captured too
            }
            Thread.sleep(400);
        }
    }
}
