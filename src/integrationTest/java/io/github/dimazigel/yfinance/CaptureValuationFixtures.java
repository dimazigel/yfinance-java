package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.internal.http.YahooClientFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refreshes the real-response valuation-measure fixtures ({@code timeseries_valuation_*.json}).
 * Run on demand: {@code CAPTURE_FIXTURES=1 ./gradlew integrationTest --tests '*CaptureValuationFixtures*'}.
 * Paced through the library's client; never use raw curl against Yahoo.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "CAPTURE_FIXTURES", matches = "1")
class CaptureValuationFixtures {

    /** The measures of Yahoo's "Valuation Measures" table, as timeseries key suffixes. */
    private static final List<String> MEASURES = List.of(
            "MarketCap", "EnterpriseValue", "PeRatio", "ForwardPeRatio", "PegRatio", "PsRatio", "PbRatio",
            "EnterprisesValueRevenueRatio", "EnterprisesValueEBITDARatio");

    /** Symbol → fixture suffix: three equities on different exchanges, a non-equity and an unknown symbol. */
    private static final Map<String, String> SYMBOLS = Map.of(
            "AAPL", "aapl", "TTE.PA", "tte_pa", "005930.KS", "005930_ks", "SPY", "spy", "ZZZZNOTREAL", "unknown");

    private static final long PERIOD_START = LocalDate.of(2016, 12, 31).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

    @Test
    void capture() throws Exception {
        var client = YahooClientFactory.apiClient(EndpointConfig.production());
        var mapper = JsonMapper.builder().build();
        var out = Path.of("src/test/resources/fixtures");

        // Each capture answers exactly the request the library makes: the nine keys of one frequency.
        for (var symbol : SYMBOLS.entrySet()) {
            String body = fetch(client, symbol.getKey(), keys("quarterly"));
            write(mapper, out.resolve("timeseries_valuation_quarterly_" + symbol.getValue() + ".json"), body);
            Thread.sleep(500);
        }
        write(mapper, out.resolve("timeseries_valuation_annual_aapl.json"), fetch(client, "AAPL", keys("annual")));
    }

    private static String keys(String prefix) {
        var keys = new ArrayList<String>();
        for (String measure : MEASURES) {
            keys.add(prefix + measure);
        }
        return String.join(",", keys);
    }

    private static String fetch(OkHttpClient client, String symbol, String type) throws IOException {
        var url = HttpUrl.get("https://query2.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries").newBuilder()
                .addPathSegment(symbol)
                .addQueryParameter("type", type)
                .addQueryParameter("period1", String.valueOf(PERIOD_START))
                .addQueryParameter("period2", String.valueOf(Instant.now().getEpochSecond()))
                .build();
        try (var response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            String body = Objects.requireNonNull(response.body(), "response body").string();
            System.out.printf("%s -> HTTP %d, %d chars%n", symbol, response.code(), body.length());
            return body;
        }
    }

    /** Pretty-prints JSON; a body that is not JSON is written as received so the failure is inspectable. */
    private static void write(JsonMapper mapper, Path file, String body) throws IOException {
        String text;
        try {
            text = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(body));
        } catch (RuntimeException notJson) {
            text = body;
        }
        Files.writeString(file, text);
    }
}
