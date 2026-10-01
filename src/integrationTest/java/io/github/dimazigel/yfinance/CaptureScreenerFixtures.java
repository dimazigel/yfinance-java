package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.internal.http.YahooClientFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    /** Every listing of Apple, most traded first: what {@code YFinance.listings(Isin)} sends. */
    private static final String CUSTOM_ISIN = "{\"offset\":0,\"size\":250,\"sortField\":\"dayvolume\",\"sortType\":\"DESC\","
            + "\"quoteType\":\"EQUITY\",\"userId\":\"\",\"userIdType\":\"guid\",\"query\":{\"operator\":\"EQ\",\"operands\":[\"isin\",\"US0378331005\"]}}";

    /**
     * {@code CAPTURE_ONLY=custom_isin_apple,fields_equity} captures just those files; refreshing all
     * of them rewrites the values the tests assert on (the day's gainers change daily).
     */
    @Test
    void capture() throws Exception {
        var client = YahooClientFactory.apiClient(EndpointConfig.production());

        capture("predefined_day_gainers", () -> get(client, predefined("day_gainers", 5)));
        capture("predefined_top_mutual_funds", () -> get(client, predefined("top_mutual_funds", 5)));
        capture("predefined_unknown", () -> get(client, predefined("no_such_screen", 5)));
        capture("custom_equity", () -> post(client, CUSTOM_EQUITY));
        capture("custom_invalid_field", () -> post(client, CUSTOM_INVALID));
        capture("custom_isin_apple", () -> post(client, CUSTOM_ISIN));
        capture("fields_equity", () -> get(client, HttpUrl.get(BASE + "/instrument/equity/fields").newBuilder()
                .addQueryParameter("lang", "en-US").addQueryParameter("region", "US").build()));
        capture("fields_mutualfund", () -> get(client, HttpUrl.get(BASE + "/instrument/mutualfund/fields").newBuilder()
                .addQueryParameter("lang", "en-US").addQueryParameter("region", "US").build()));
    }

    private interface Fetch {
        String get() throws IOException;
    }

    private static void capture(String name, Fetch fetch) throws Exception {
        String only = System.getenv("CAPTURE_ONLY");
        if (only != null && !only.isBlank() && !List.of(only.split(",")).contains(name)) {
            return;
        }
        var out = Path.of("src/test/resources/fixtures/screener");
        Files.createDirectories(out);
        write(JsonMapper.builder().build(), out.resolve(name + ".json"), fetch.get());
        Thread.sleep(500);
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
