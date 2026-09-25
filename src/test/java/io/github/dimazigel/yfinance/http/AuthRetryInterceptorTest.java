package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.valueobject.Crumb;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthRetryInterceptorTest {

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
    void on401InvalidatesCrumbAndRetriesOnceWithFreshCrumb() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        var crumb = new AtomicReference<>("stale");
        var invalidations = new AtomicInteger();
        Runnable onAuthFailure = () -> {
            invalidations.incrementAndGet();
            crumb.set("fresh");
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AuthRetryInterceptor(onAuthFailure))
                .addInterceptor(new CrumbInterceptor(() -> Crumb.of(crumb.get())))
                .build();

        try (Response response = client.newCall(
                        new Request.Builder().url(server.url("/v8/finance/chart/AAPL")).build())
                .execute()) {
            assertThat(response.code()).isEqualTo(200);
        }

        assertThat(invalidations).hasValue(1);
        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(server.takeRequest().getRequestUrl().queryParameter("crumb")).isEqualTo("stale");
        assertThat(server.takeRequest().getRequestUrl().queryParameter("crumb")).isEqualTo("fresh");
    }

    @Test
    void successfulResponsePassesThroughWithoutRetry() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        var invalidations = new AtomicInteger();

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AuthRetryInterceptor(invalidations::incrementAndGet))
                .build();

        try (Response response = client.newCall(
                        new Request.Builder().url(server.url("/ok")).build())
                .execute()) {
            assertThat(response.code()).isEqualTo(200);
        }
        assertThat(invalidations).hasValue(0);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}
