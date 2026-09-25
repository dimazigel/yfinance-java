package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransientErrorRetryInterceptorTest {

    private MockWebServer server;
    private final List<Duration> sleeps = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private OkHttpClient client(RetryConfig config) {
        return new OkHttpClient.Builder()
                .addInterceptor(new TransientErrorRetryInterceptor(config, sleeps::add))
                .build();
    }

    private int call(OkHttpClient client) throws Exception {
        try (var response = client.newCall(new Request.Builder().url(server.url("/v1/finance/lookup")).build()).execute()) {
            return response.code();
        }
    }

    @Test
    void retriesServerErrorsWithExponentialBackoff() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("<html>Yahoo! - Error report</html>"));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(200));

        int code = call(client(new RetryConfig(3, Duration.ofMillis(100), Duration.ofSeconds(5))));

        assertThat(code).isEqualTo(200);
        assertThat(server.getRequestCount()).isEqualTo(3);
        assertThat(sleeps).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
    }

    @Test
    void givesUpAfterMaxAttemptsAndReturnsTheLastResponse() throws Exception {
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(502));
        }

        int code = call(client(new RetryConfig(3, Duration.ofMillis(1), Duration.ofSeconds(1))));

        assertThat(code).isEqualTo(502);
        assertThat(server.getRequestCount()).isEqualTo(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void doesNotRetryClientErrorsOrRateLimits() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));
        assertThat(call(client(RetryConfig.defaults()))).isEqualTo(404);

        server.enqueue(new MockResponse().setResponseCode(429));
        assertThat(call(client(RetryConfig.defaults()))).isEqualTo(429); // the rate limiter's job

        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void honoursRetryAfterAndCapsTheDelay() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "2"));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(200));

        call(client(new RetryConfig(3, Duration.ofSeconds(2), Duration.ofSeconds(3))));

        // attempt 2 honours Retry-After (2 s, under the cap); attempt 3 would back off 4 s, capped to 3 s
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    @Test
    void disabledConfigNeverRetries() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThat(call(client(RetryConfig.disabled()))).isEqualTo(500);
        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void configIsValidated() {
        assertThatThrownBy(() -> new RetryConfig(0, Duration.ofMillis(1), Duration.ofMillis(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryConfig(2, Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(RetryConfig.defaults().maxAttempts()).isEqualTo(3);
        assertThat(RetryConfig.disabled().maxAttempts()).isEqualTo(1);
    }
}
