package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.http.YahooClientFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refreshes the real-response fixtures under src/test/resources/fixtures/options. Run on demand:
 * {@code CAPTURE_FIXTURES=1 ./gradlew integrationTest --tests '*CaptureOptionFixtures*'}.
 * Paced through the library's client; never use raw curl against Yahoo.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "CAPTURE_FIXTURES", matches = "1")
class CaptureOptionFixtures {

    private static final List<String> SYMBOLS = List.of("AAPL", "SPY", "^SPX", "GLD", "PLUG", "TSLA");

    @Test
    void capture() throws Exception {
        var client = YahooClientFactory.apiClient(EndpointConfig.production());
        var mapper = JsonMapper.builder().build();
        var out = Path.of("src/test/resources/fixtures/options");
        Files.createDirectories(out);

        for (String symbol : SYMBOLS) {
            JsonNode body = fetch(client, symbol, null);
            write(mapper, out.resolve("options_" + safe(symbol) + ".json"), body);
            Thread.sleep(500);

            if (symbol.equals("AAPL")) {
                List<Long> expirationDates = expirationDates(body);
                if (expirationDates.size() > 1) {
                    long second = expirationDates.get(1);
                    JsonNode secondBody = fetch(client, symbol, second);
                    write(mapper, out.resolve("options_AAPL_" + second + ".json"), secondBody);
                    Thread.sleep(500);
                }
            }
        }
    }

    private static JsonNode fetch(OkHttpClient client, String symbol, @Nullable Long date) throws IOException {
        var urlBuilder = HttpUrl.get("https://query1.finance.yahoo.com/v7/finance/options")
                .newBuilder()
                .addPathSegment(symbol);
        if (date != null) {
            urlBuilder.addQueryParameter("date", String.valueOf(date));
        }
        var request = new Request.Builder().url(urlBuilder.build()).build();
        try (var response = client.newCall(request).execute()) {
            String body = Objects.requireNonNull(response.body(), "response body").string();
            return JsonMapper.builder().build().readTree(body);
        }
    }

    private static List<Long> expirationDates(JsonNode chainBody) {
        var dates = new ArrayList<Long>();
        JsonNode results = chainBody.path("optionChain").path("result");
        if (results.isArray() && !results.isEmpty()) {
            for (JsonNode date : results.get(0).path("expirationDates")) {
                dates.add(date.asLong());
            }
        }
        return dates;
    }

    private static void write(JsonMapper mapper, Path file, JsonNode body) throws IOException {
        Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(body));
    }

    /** Non-alphanumerics replaced with {@code _}, matching the fixture-naming convention. */
    private static String safe(String symbol) {
        return symbol.replaceAll("[^A-Za-z0-9]", "_");
    }
}
