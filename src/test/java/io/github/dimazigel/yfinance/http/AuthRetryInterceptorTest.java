package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.auth.CrumbStore;
import io.github.dimazigel.yfinance.valueobject.Crumb;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jspecify.annotations.Nullable;
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

    @Test
    void passesTheRejectedCrumbToTheAuthFailureHook() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        var crumb = new AtomicReference<>("stale");
        var rejected = new AtomicReference<@Nullable String>("unset");
        Consumer<@Nullable String> onAuthFailure = r -> {
            rejected.set(r);
            crumb.set("fresh");
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(AuthRetryInterceptor.onRejectedCrumb(onAuthFailure))
                .addInterceptor(new CrumbInterceptor(() -> Crumb.of(crumb.get())))
                .build();

        try (Response response = client.newCall(new Request.Builder().url(server.url("/v8/finance/chart/AAPL")).build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }

        assertThat(rejected.get()).as("the crumb Yahoo rejected, read off the final request").isEqualTo("stale");
        assertThat(server.takeRequest().getRequestUrl().queryParameter("crumb")).isEqualTo("stale");
        assertThat(server.takeRequest().getRequestUrl().queryParameter("crumb")).isEqualTo("fresh");
    }

    @Test
    void doesNotRetryWhenTheRequestHadNoCrumbAndNoneIsAvailable() throws Exception {   // review, minor 9
        // During a crumb cooldown every request goes out crumbless; a 401 then must not cost a second
        // crumbless round trip that can only 401 again.
        server.enqueue(new MockResponse().setResponseCode(401));
        var rejected = new AtomicReference<@Nullable String>("unset");
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(AuthRetryInterceptor.onRejectedCrumb(rejected::set, () -> null))
                .addInterceptor(new CrumbInterceptor(() -> null))
                .build();

        try (Response response = client.newCall(new Request.Builder().url(server.url("/data")).build()).execute()) {
            assertThat(response.code()).isEqualTo(401);
        }

        assertThat(rejected.get()).as("the hook still runs (a no-op for a null crumb)").isNull();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void closesTheRejectedResponseEvenWhenTheCrumbSupplierThrows() {   // re-review, minor 2
        // tryGetCrumb() rethrows a rejected handshake (YFAuthException); the 401 body must not leak.
        server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        Supplier<@Nullable Crumb> throwing = () -> {
            throw new io.github.dimazigel.yfinance.exception.YFAuthException("Failed to obtain crumb: HTTP 403");
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(AuthRetryInterceptor.onRejectedCrumb(rejected -> { }, throwing))
                .addInterceptor(chain -> {   // below the retry: wrap the body so close() is observable
                    Response response = chain.proceed(chain.request());
                    okhttp3.ResponseBody body = java.util.Objects.requireNonNull(response.body());
                    return response.newBuilder().body(new okhttp3.ResponseBody() {
                        @Override public okhttp3.@Nullable MediaType contentType() { return body.contentType(); }
                        @Override public long contentLength() { return body.contentLength(); }
                        @Override public okio.BufferedSource source() { return body.source(); }
                        @Override public void close() { closed.set(true); body.close(); }
                    }).build();
                })
                .addInterceptor(new CrumbInterceptor(() -> null))
                .build();

        assertThatThrownBy(() -> client.newCall(new Request.Builder().url(server.url("/data")).build()).execute())
                .isInstanceOf(io.github.dimazigel.yfinance.exception.YFAuthException.class);

        assertThat(closed).as("the rejected response was closed before the supplier ran").isTrue();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void retriesACrumblessRequestOnceACrumbIsAvailable() throws Exception {   // review, minor 9
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        var crumb = new AtomicReference<@Nullable String>(null);
        Supplier<@Nullable Crumb> supplier = () -> crumb.get() == null ? null : Crumb.of(crumb.get());
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(AuthRetryInterceptor.onRejectedCrumb(r -> crumb.set("fresh"), supplier))
                .addInterceptor(new CrumbInterceptor(supplier))
                .build();

        try (Response response = client.newCall(new Request.Builder().url(server.url("/data")).build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }

        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(server.takeRequest().getRequestUrl().queryParameter("crumb")).isNull();
        assertThat(server.takeRequest().getRequestUrl().queryParameter("crumb")).isEqualTo("fresh");
    }

    @Test
    void concurrent401sRefreshTheCrumbOnce() throws Exception {
        // Scenario test, not a guaranteed RED for unconditional invalidation (that interleaving yields 2 or
        // 3 handshakes); CrumbStoreTest.invalidateWithTheRejectedCrumbClearsOnlyThatCrumb is the deterministic proof.
        var handshakes = new AtomicInteger();
        var bothRejected = new CountDownLatch(2);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String path = request.getRequestUrl().encodedPath();
                if (path.equals("/v1/test/getcrumb")) {
                    return new MockResponse().setResponseCode(200).setBody("crumb-" + handshakes.incrementAndGet());
                }
                if (path.equals("/data")) {
                    if ("crumb-1".equals(request.getRequestUrl().queryParameter("crumb"))) {
                        bothRejected.countDown();
                        bothRejected.await(5, TimeUnit.SECONDS);   // release both 401s together
                        return new MockResponse().setResponseCode(401);
                    }
                    return new MockResponse().setResponseCode(200).setBody("{}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        var config = EndpointConfig.production().withHosts(server.url("/"));
        var crumbStore = new CrumbStore(new OkHttpClient(), config);
        crumbStore.getCrumb();   // seeded with crumb-1
        var unconditional = new AuthRetryInterceptor(crumbStore::invalidate);   // the 1.1.0 call shape must still compile (review, important 2)
        assertThat(unconditional).isNotNull();
        Supplier<@Nullable Crumb> supplier = () -> crumbStore.tryGetCrumb().orElse(null);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(AuthRetryInterceptor.onRejectedCrumb(crumbStore::invalidate, supplier))
                .addInterceptor(new CrumbInterceptor(supplier))
                .build();
        var request = new Request.Builder().url(server.url("/data")).build();

        try (var pool = Executors.newFixedThreadPool(2)) {
            var codes = pool.invokeAll(List.of(
                    () -> { try (var r = client.newCall(request).execute()) { return r.code(); } },
                    () -> { try (var r = client.newCall(request).execute()) { return r.code(); } }));
            assertThat(codes.get(0).get()).isEqualTo(200);
            assertThat(codes.get(1).get()).isEqualTo(200);
        }

        assertThat(handshakes.get()).as("one seed handshake + exactly one refresh for the burst").isEqualTo(2);
        assertThat(crumbStore.getCrumb()).isEqualTo(Crumb.of("crumb-2"));
    }
}
