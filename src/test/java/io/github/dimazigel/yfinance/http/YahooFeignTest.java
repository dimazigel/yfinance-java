package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.Param;
import feign.RequestLine;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFHttpException;
import io.github.dimazigel.yfinance.exception.YFRateLimitException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The Feign boundary: request shape (path/query encoding, omitted nulls), the exception contract for
 * every failure class, and that the configured OkHttp client is used as is (timeouts, one attempt).
 */
class YahooFeignTest {

    interface ProbeApi {
        @RequestLine("GET /probe/{symbol}?range={range}&period1={period1}&modules={modules}&flag={flag}")
        JsonNode probe(@Param("symbol") String symbol, @Param("range") @Nullable String range,
                @Param("period1") @Nullable Long period1, @Param("modules") @Nullable String modules,
                @Param("flag") boolean flag);
    }

    private MockWebServer server;
    private final AtomicInteger attempts = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private ProbeApi api(OkHttpClient client) {
        return YahooFeign.builder(client, YahooJsonMapper.create()).target(ProbeApi.class, server.url("/").toString());
    }

    private OkHttpClient countingClient(Duration readTimeout) {
        return new OkHttpClient.Builder()
                .readTimeout(readTimeout)
                .addInterceptor(chain -> {
                    attempts.incrementAndGet();
                    return chain.proceed(chain.request());
                })
                .build();
    }

    private static HttpUrl sent(MockWebServer server) throws InterruptedException {
        return server.url(java.util.Objects.requireNonNull(server.takeRequest().getPath()));
    }

    @Test
    void pathAndQueryValuesRoundTrip() throws Exception {   // Review Focus 2
        server.enqueue(new MockResponse().setBody("{}"));
        server.enqueue(new MockResponse().setBody("{}"));
        var api = api(new OkHttpClient());

        api.probe("^GSPC", "1mo", null, "price,summaryDetail", true);
        HttpUrl first = sent(server);
        assertThat(first.pathSegments()).containsExactly("probe", "^GSPC");
        assertThat(first.queryParameter("range")).isEqualTo("1mo");
        assertThat(first.queryParameter("modules")).isEqualTo("price,summaryDetail");
        assertThat(first.queryParameter("flag")).isEqualTo("true");

        api.probe("EURUSD=X", null, 1700000000L, null, false);
        HttpUrl second = sent(server);
        assertThat(second.pathSegments()).containsExactly("probe", "EURUSD=X");
        assertThat(second.queryParameter("period1")).isEqualTo("1700000000");
    }

    @Test
    void nullParamsAreOmittedFromTheQuery() throws Exception {   // Review Focus 1
        server.enqueue(new MockResponse().setBody("{}"));
        api(new OkHttpClient()).probe("AAPL", null, null, null, false);
        HttpUrl url = sent(server);
        assertThat(url.queryParameterNames()).containsExactly("flag");
    }

    @Test
    void successfulBodyDecodesToJson() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"quoteResponse\":{\"result\":[{\"symbol\":\"AAPL\",\"regularMarketPrice\":{\"raw\":1.5}}]}}"));
        JsonNode node = api(new OkHttpClient()).probe("AAPL", null, null, null, true);
        assertThat(node.path("quoteResponse").path("result").path(0).path("symbol").asText()).isEqualTo("AAPL");
    }

    @Test
    void httpErrorMessageIncludesStatusPathAndBody() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("upstream boom details"));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isInstanceOf(YFHttpException.class)
                .hasMessage("Yahoo Finance returned HTTP 500 for /probe/AAPL: upstream boom details")
                .satisfies(e -> {
                    assertThat(((YFHttpException) e).status()).isEqualTo(500);
                    assertThat(((YFHttpException) e).path()).isEqualTo("/probe/AAPL");
                });
    }

    @Test
    void httpErrorWithYahooEnvelopeSurfacesItsDescription() {
        server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json")
                .setBody("{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\","
                        + "\"description\":\"No data found, symbol may be delisted\"}}}"));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("NOPE", null, null, null, true))
                .isInstanceOf(YFHttpException.class)
                .hasMessage("Yahoo Finance returned HTTP 404 for /probe/NOPE: No data found, symbol may be delisted");
    }

    @Test
    void htmlErrorPagesAreSummarisedNotQuoted() {
        var html = "<!doctype html public \"-//W3C//DTD HTML 4.01//EN\"><html><head><title>Yahoo! - Error report</title></head></html>";
        server.enqueue(new MockResponse().setResponseCode(500).setBody(html));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isInstanceOf(YFHttpException.class)
                .hasMessage("Yahoo Finance returned HTTP 500 for /probe/AAPL: HTML error page (" + html.length() + " bytes)");
    }

    @Test
    void rateLimitCarriesRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "12").setBody("slow down"));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isInstanceOf(YFRateLimitException.class)
                .hasMessage("Yahoo Finance rate limit hit (HTTP 429) for /probe/AAPL: slow down")
                .satisfies(e -> assertThat(((YFRateLimitException) e).retryAfter()).contains(Duration.ofSeconds(12)));
    }

    @Test
    void rateLimitWithoutHeaderHasEmptyRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("slow down"));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isInstanceOf(YFRateLimitException.class)
                .satisfies(e -> assertThat(((YFRateLimitException) e).retryAfter()).isEmpty());
    }

    @Test
    void emptyBodyIsADataException() {   // Review Focus 4
        server.enqueue(new MockResponse().setResponseCode(200));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isExactlyInstanceOf(YFDataException.class)
                .hasMessage("Yahoo Finance returned an empty body");
    }

    @Test
    void malformedJsonIsADataException() {   // Review Focus 4
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("<html>consent page</html>"));
        assertThatThrownBy(() -> api(new OkHttpClient()).probe("AAPL", null, null, null, true))
                .isExactlyInstanceOf(YFDataException.class)
                .hasMessage("Yahoo Finance returned malformed JSON for /probe/AAPL")
                .hasCauseInstanceOf(tools.jackson.core.JacksonException.class);
    }

    @Test
    void ioFailureIsADataExceptionAfterOneAttempt() throws Exception {   // Review Focus 3
        var api = api(countingClient(Duration.ofSeconds(5)));
        server.shutdown();
        assertThatThrownBy(() -> api.probe("AAPL", null, null, null, true))
                .isExactlyInstanceOf(YFDataException.class)
                .hasMessage("I/O error calling Yahoo Finance")
                .hasCauseInstanceOf(java.io.IOException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void clientTimeoutsAreHonoured() {   // Review Focus 5
        server.enqueue(new MockResponse().setBody("{}").setBodyDelay(3, TimeUnit.SECONDS));
        var api = api(countingClient(Duration.ofMillis(300)));
        long start = System.nanoTime();
        assertThatThrownBy(() -> api.probe("AAPL", null, null, null, true)).isExactlyInstanceOf(YFDataException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
        assertThat(attempts).hasValue(1);
    }
}
