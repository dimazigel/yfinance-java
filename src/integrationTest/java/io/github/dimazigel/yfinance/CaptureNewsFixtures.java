package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.internal.http.YahooClientFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refreshes the real-response fixtures under src/test/resources/fixtures/news. Run on demand:
 * {@code CAPTURE_FIXTURES=1 ./gradlew integrationTest --tests '*CaptureNewsFixtures*'}.
 * Paced through the library's client; never use raw curl against Yahoo.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "CAPTURE_FIXTURES", matches = "1")
class CaptureNewsFixtures {

    /** Tab name in the fixture file → the endpoint's {@code queryRef}. */
    private static final Map<String, String> TABS =
            Map.of("news", "latestNews", "all", "newsAll", "press", "pressRelease");

    /** Symbols captured on the news tab only; AAPL is captured on every tab. */
    private static final List<String> NEWS_ONLY = List.of("SPY", "BTC-USD", "TTE.PA");

    /** A symbol Yahoo does not know: answered with an empty stream, captured as {@code ncp_news_unknown.json}. */
    private static final String UNKNOWN = "ZZZZNOTREAL";

    private static final int COUNT = 10;

    @Test
    void capture() throws Exception {
        var client = YahooClientFactory.apiClient(EndpointConfig.production());
        var mapper = JsonMapper.builder().build();
        var out = Path.of("src/test/resources/fixtures/news");
        Files.createDirectories(out);

        for (var tab : TABS.entrySet()) {
            write(mapper, out.resolve("ncp_" + tab.getKey() + "_AAPL.json"), fetch(client, mapper, "AAPL", tab.getValue()));
            Thread.sleep(500);
        }
        for (String symbol : NEWS_ONLY) {
            write(mapper, out.resolve("ncp_news_" + safe(symbol) + ".json"), fetch(client, mapper, symbol, "latestNews"));
            Thread.sleep(500);
        }
        write(mapper, out.resolve("ncp_news_unknown.json"), fetch(client, mapper, UNKNOWN, "latestNews"));
    }

    private static String fetch(OkHttpClient client, JsonMapper mapper, String symbol, String queryRef) throws IOException {
        var url = HttpUrl.get("https://finance.yahoo.com/xhr/ncp").newBuilder()
                .addQueryParameter("queryRef", queryRef)
                .addQueryParameter("serviceKey", "ncp_fin")
                .build();
        String payload = mapper.writeValueAsString(
                Map.of("serviceConfig", Map.of("snippetCount", COUNT, "s", List.of(symbol))));
        var request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload, MediaType.get("application/json")))
                .build();
        try (var response = client.newCall(request).execute()) {
            String body = Objects.requireNonNull(response.body(), "response body").string();
            System.out.printf("%s %s -> HTTP %d, %d chars, %s%n",
                    symbol, queryRef, response.code(), body.length(), response.header("Content-Type"));
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

    /** Non-alphanumerics replaced with {@code _}, matching the fixture-naming convention. */
    private static String safe(String symbol) {
        return symbol.replaceAll("[^A-Za-z0-9]", "_");
    }
}
