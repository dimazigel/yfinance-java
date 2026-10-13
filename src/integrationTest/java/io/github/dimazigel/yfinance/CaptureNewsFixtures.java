package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.enums.NewsTab;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.internal.api.NewsApi;
import io.github.dimazigel.yfinance.internal.dto.news.NewsRequest;
import io.github.dimazigel.yfinance.internal.http.YahooClientFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refreshes {@code src/test/resources/fixtures/news/} from Yahoo's news gateway: the three tabs for
 * AAPL, the news tab for three more symbols, and a symbol Yahoo has no stream for. The body sent is
 * the library's own {@link NewsRequest}, so a fixture is exactly what {@code NewsService} would get.
 *
 * <p>Run with {@code CAPTURE_FIXTURES=1 ./gradlew integrationTest --tests '*CaptureNewsFixtures'}.
 * Refreshing rewrites the values {@code NewsServiceTest} asserts on.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "CAPTURE_FIXTURES", matches = "1")
class CaptureNewsFixtures {

    private static final Map<String, NewsTab> TABS =
            Map.of("news", NewsTab.NEWS, "all", NewsTab.ALL, "press", NewsTab.PRESS_RELEASES);
    private static final List<String> NEWS_ONLY = List.of("SPY", "BTC-USD", "TTE.PA");
    private static final String UNKNOWN = "ZZZZNOTREAL";
    private static final int COUNT = 10;

    @Test
    void capture() throws Exception {
        var config = EndpointConfig.production();
        var client = YahooClientFactory.apiClient(config);
        var mapper = JsonMapper.builder().build();
        var out = Path.of("src/test/resources/fixtures/news");
        Files.createDirectories(out);
        for (var tab : TABS.entrySet()) {
            write(mapper, out.resolve("gateway_" + tab.getKey() + "_AAPL.json"), fetch(config, client, mapper, "AAPL", tab.getValue()));
            Thread.sleep(500);
        }
        for (String symbol : NEWS_ONLY) {
            write(mapper, out.resolve("gateway_news_" + safe(symbol) + ".json"), fetch(config, client, mapper, symbol, NewsTab.NEWS));
            Thread.sleep(500);
        }
        write(mapper, out.resolve("gateway_news_unknown.json"), fetch(config, client, mapper, UNKNOWN, NewsTab.NEWS));
    }

    private static String fetch(EndpointConfig config, OkHttpClient client, JsonMapper mapper, String symbol, NewsTab tab)
            throws IOException {
        String payload = mapper.writeValueAsString(NewsRequest.tickerNews(tab, symbol, COUNT));
        var request = new Request.Builder()
                .url(config.newsGatewayBase())
                .header(NewsApi.CLIENT_NAME_HEADER, "finance")
                .post(RequestBody.create(payload, MediaType.get("application/json")))
                .build();
        try (var response = client.newCall(request).execute()) {
            String body = Objects.requireNonNull(response.body(), "response body").string();
            System.out.printf("%s %s -> HTTP %d, %d chars, %s%n",
                    symbol, tab, response.code(), body.length(), response.header("Content-Type"));
            return body;
        }
    }

    private static void write(JsonMapper mapper, Path file, String body) throws IOException {
        String text;
        try {
            text = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(body));
        } catch (RuntimeException notJson) {
            text = body;
        }
        Files.writeString(file, text);
    }

    private static String safe(String symbol) {
        return symbol.replaceAll("[^A-Za-z0-9]", "_");
    }
}
