package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.logging.LogContext;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LogContextInterceptorTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
        MDC.clear();
    }

    @Test
    void endpointIsVisibleToDownstreamInterceptorsAndClearedAfterwards() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        var seen = new AtomicReference<String>();
        var client = new OkHttpClient.Builder()
                .addInterceptor(new LogContextInterceptor())
                .addInterceptor(chain -> {
                    seen.set(MDC.get(LogContext.ENDPOINT));
                    return chain.proceed(chain.request());
                })
                .build();

        client.newCall(new Request.Builder().url(server.url("/v8/finance/chart/AAPL?range=1d")).build())
                .execute().close();

        assertThat(seen.get()).isEqualTo("/v8/finance/chart/AAPL"); // path only, no query string
        assertThat(MDC.get(LogContext.ENDPOINT)).isNull();
    }

    @Test
    void previousEndpointIsRestoredEvenWhenTheCallFails() {
        MDC.put(LogContext.ENDPOINT, "/outer");
        var client = new OkHttpClient.Builder()
                .addInterceptor(new LogContextInterceptor())
                .addInterceptor(chain -> {
                    throw new java.io.IOException("boom");
                })
                .build();

        try {
            client.newCall(new Request.Builder().url(server.url("/x")).build()).execute();
        } catch (java.io.IOException expected) {
            // fine
        }
        assertThat(MDC.get(LogContext.ENDPOINT)).isEqualTo("/outer");
    }
}
