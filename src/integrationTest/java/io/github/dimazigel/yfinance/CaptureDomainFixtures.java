package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.internal.http.YahooClientFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refreshes the real-response fixtures under src/test/resources/fixtures/domain (sectors and
 * industries). Run on demand:
 * {@code CAPTURE_FIXTURES=1 ./gradlew integrationTest --tests '*CaptureDomainFixtures*'}.
 * Paced through the library's client; never use raw curl against Yahoo.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "CAPTURE_FIXTURES", matches = "1")
class CaptureDomainFixtures {

    private static final List<String> SECTORS = List.of("technology", "financial-services", "no-such-sector");
    private static final List<String> INDUSTRIES = List.of("semiconductors", "banks-diversified", "no-such-industry");

    @Test
    void capture() throws Exception {
        var client = YahooClientFactory.apiClient(EndpointConfig.production());
        var mapper = JsonMapper.builder().build();
        var out = Path.of("src/test/resources/fixtures/domain");
        Files.createDirectories(out);
        for (String key : SECTORS) {
            write(mapper, out.resolve("sector_" + key.replace('-', '_') + ".json"), fetch(client, "sectors", key));
            Thread.sleep(500);
        }
        for (String key : INDUSTRIES) {
            write(mapper, out.resolve("industry_" + key.replace('-', '_') + ".json"), fetch(client, "industries", key));
            Thread.sleep(500);
        }
    }

    private static String fetch(OkHttpClient client, String kind, String key) throws IOException {
        var url = HttpUrl.get("https://query1.finance.yahoo.com/v1/finance").newBuilder()
                .addPathSegment(kind)
                .addPathSegment(key)
                .addQueryParameter("formatted", "false")
                .addQueryParameter("withReturns", "true")
                .addQueryParameter("lang", "en-US")
                .addQueryParameter("region", "US")
                .build();
        try (var response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            String body = Objects.requireNonNull(response.body(), "response body").string();
            System.out.printf("%s/%s -> HTTP %d, %d chars%n", kind, key, response.code(), body.length());
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
