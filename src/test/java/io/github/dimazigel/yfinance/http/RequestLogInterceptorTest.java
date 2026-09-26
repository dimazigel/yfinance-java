package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.valueobject.Crumb;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestLogInterceptorTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void logsMethodPathQueryStatusSizeAndDurationAtDebug() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        var client = new OkHttpClient.Builder().addInterceptor(new RequestLogInterceptor()).build();

        try (var log = LogCapture.of(RequestLogInterceptor.class)) {
            client.newCall(new Request.Builder().url(server.url("/v8/finance/chart/AAPL?range=1mo&interval=1d")).build())
                    .execute().close();

            assertThat(log.messages(Level.DEBUG)).singleElement().satisfies(m -> assertThat(m)
                    .startsWith("GET /v8/finance/chart/AAPL?range=1mo&interval=1d -> 200")
                    .contains("11 B")
                    .matches(".* in \\d+ ms$"));
            var kv = log.events().getFirst().getKeyValuePairs().stream()
                    .collect(java.util.stream.Collectors.toMap(p -> p.key, p -> p.value));
            assertThat(kv).containsEntry("status", 200).containsKeys("durationMs", "bytes");
        }
    }

    @Test
    void neverLogsTheCrumb() throws Exception {
        // Even if placed below CrumbInterceptor (it is not), the credential must not reach the log.
        server.enqueue(new MockResponse().setResponseCode(200));
        var client = new OkHttpClient.Builder()
                .addInterceptor(new CrumbInterceptor(() -> Crumb.of("s3cr3t.crumb")))
                .addInterceptor(new RequestLogInterceptor())
                .build();

        try (var log = LogCapture.of(RequestLogInterceptor.class)) {
            client.newCall(new Request.Builder().url(server.url("/v7/finance/quote?symbols=AAPL")).build())
                    .execute().close();

            assertThat(log.messages(Level.DEBUG)).singleElement().satisfies(m -> assertThat(m)
                    .doesNotContain("s3cr3t")
                    .contains("crumb=***")
                    .contains("symbols=AAPL"));
        }
    }

    @Test
    void logsFailuresBeforeRethrowing() throws Exception {
        server.shutdown(); // connection refused
        var client = new OkHttpClient.Builder().addInterceptor(new RequestLogInterceptor()).build();

        try (var log = LogCapture.of(RequestLogInterceptor.class)) {
            assertThatThrownBy(() -> client.newCall(new Request.Builder().url(server.url("/x")).build()).execute())
                    .isInstanceOf(IOException.class);
            assertThat(log.messages(Level.DEBUG)).singleElement().satisfies(m -> assertThat(m)
                    .startsWith("GET /x failed after").contains("ms:"));
        }
    }
}
