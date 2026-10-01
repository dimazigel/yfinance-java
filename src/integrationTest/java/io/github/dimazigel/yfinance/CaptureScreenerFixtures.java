package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.internal.http.YahooClientFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Refreshes the real-response fixtures under src/test/resources/fixtures/screener. Run on demand:
 * {@code CAPTURE_FIXTURES=1 ./gradlew integrationTest --tests '*CaptureScreenerFixtures*'}.
 * Paced through the library's client; never use raw curl against Yahoo.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "CAPTURE_FIXTURES", matches = "1")
class CaptureScreenerFixtures {

    private static final String BASE = "https://query1.finance.yahoo.com/v1/finance/screener";

    /** Large-cap US technology: region = us AND sector = Technology AND intradaymarketcap > 100e9. */
    private static final String CUSTOM_EQUITY = "{\"offset\":0,\"size\":5,\"sortField\":\"intradaymarketcap\",\"sortType\":\"DESC\","
            + "\"quoteType\":\"EQUITY\",\"userId\":\"\",\"userIdType\":\"guid\",\"query\":{\"operator\":\"AND\",\"operands\":["
            + "{\"operator\":\"EQ\",\"operands\":[\"region\",\"us\"]},"
            + "{\"operator\":\"EQ\",\"operands\":[\"sector\",\"Technology\"]},"
            + "{\"operator\":\"GT\",\"operands\":[\"intradaymarketcap\",100000000000]}]}}";

    private static final String CUSTOM_INVALID = "{\"offset\":0,\"size\":5,\"sortField\":\"intradaymarketcap\",\"sortType\":\"DESC\","
            + "\"quoteType\":\"EQUITY\",\"userId\":\"\",\"userIdType\":\"guid\",\"query\":{\"operator\":\"GT\",\"operands\":[\"no_such_field\",1]}}";

    @Test
    void capture() throws Exception {
        var client = YahooClientFactory.apiClient(EndpointConfig.production());
        var mapper = JsonMapper.builder().build();
        var out = Path.of("src/test/resources/fixtures/screener");
        Files.createDirectories(out);

        write(mapper, out.resolve("predefined_day_gainers.json"), get(client, predefined("day_gainers", 5)));
        Thread.sleep(500);
        write(mapper, out.resolve("predefined_top_mutual_funds.json"), get(client, predefined("top_mutual_funds", 5)));
        Thread.sleep(500);
        write(mapper, out.resolve("predefined_unknown.json"), get(client, predefined("no_such_screen", 5)));
        Thread.sleep(500);
        write(mapper, out.resolve("custom_equity.json"), post(client, CUSTOM_EQUITY));
        Thread.sleep(500);
        write(mapper, out.resolve("custom_invalid_field.json"), post(client, CUSTOM_INVALID));
        Thread.sleep(500);
        write(mapper, out.resolve("fields_equity.json"), get(client, HttpUrl.get(BASE + "/instrument/equity/fields").newBuilder()
                .addQueryParameter("lang", "en-US").addQueryParameter("region", "US").build()));
        Thread.sleep(500);
        write(mapper, out.resolve("fields_mutualfund.json"), get(client, HttpUrl.get(BASE + "/instrument/mutualfund/fields").newBuilder()
                .addQueryParameter("lang", "en-US").addQueryParameter("region", "US").build()));
    }

    private static HttpUrl predefined(String id, int count) {
        return HttpUrl.get(BASE + "/predefined/saved").newBuilder()
                .addQueryParameter("scrIds", id)
                .addQueryParameter("count", String.valueOf(count))
                .addQueryParameter("formatted", "false")
                .addQueryParameter("corsDomain", "finance.yahoo.com")
                .addQueryParameter("lang", "en-US")
                .addQueryParameter("region", "US")
                .build();
    }

    private static String get(OkHttpClient client, HttpUrl url) throws IOException {
        return execute(client, new Request.Builder().url(url).build());
    }

    private static String post(OkHttpClient client, String json) throws IOException {
        var url = HttpUrl.get(BASE).newBuilder()
                .addQueryParameter("formatted", "false")
                .addQueryParameter("corsDomain", "finance.yahoo.com")
                .addQueryParameter("lang", "en-US")
                .addQueryParameter("region", "US")
                .build();
        return execute(client, new Request.Builder().url(url).post(RequestBody.create(json, MediaType.get("application/json"))).build());
    }

    private static String execute(OkHttpClient client, Request request) throws IOException {
        try (var response = client.newCall(request).execute()) {
            String body = Objects.requireNonNull(response.body(), "response body").string();
            System.out.printf("%s %s -> HTTP %d, %d chars%n", request.method(), request.url().encodedPath()
                    + "?" + String.valueOf(request.url().queryParameter("scrIds")), response.code(), body.length());
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
