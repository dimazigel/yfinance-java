package io.ziggy.yfinance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ziggy.yfinance.exception.YFAuthException;
import io.ziggy.yfinance.http.EndpointConfig;
import io.ziggy.yfinance.http.YahooClientFactory;
import io.ziggy.yfinance.valueobject.Crumb;
import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrumbStoreTest {

    private MockWebServer server;
    private CrumbStore crumbStore;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        HttpUrl base = server.url("/");
        EndpointConfig config = EndpointConfig.production().withHosts(base).withUserAgent("test-agent/1.0");
        OkHttpClient client = YahooClientFactory.baseClient(config);
        crumbStore = new CrumbStore(client, config);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchesCookieThenCrumb() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404)); // fc.yahoo.com cookie bootstrap
        server.enqueue(new MockResponse().setResponseCode(200).setBody("abc.crumb123"));

        Crumb crumb = crumbStore.getCrumb();

        assertThat(crumb).isEqualTo(Crumb.of("abc.crumb123"));
        RecordedRequest cookieReq = server.takeRequest();
        RecordedRequest crumbReq = server.takeRequest();
        assertThat(crumbReq.getPath()).isEqualTo("/v1/test/getcrumb");
        assertThat(crumbReq.getHeader("User-Agent")).isEqualTo("test-agent/1.0");
    }

    @Test
    void cachesCrumbAcrossCalls() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("cached-crumb"));

        Crumb first = crumbStore.getCrumb();
        Crumb second = crumbStore.getCrumb();

        assertThat(second).isEqualTo(first);
        assertThat(server.getRequestCount()).isEqualTo(2); // not re-fetched
    }

    @Test
    void invalidateForcesReFetch() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("first-crumb"));
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("second-crumb"));

        Crumb first = crumbStore.getCrumb();
        crumbStore.invalidate();
        Crumb second = crumbStore.getCrumb();

        assertThat(first).isEqualTo(Crumb.of("first-crumb"));
        assertThat(second).isEqualTo(Crumb.of("second-crumb"));
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    void throwsWhenCrumbBlank() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("  "));

        assertThatThrownBy(() -> crumbStore.getCrumb()).isInstanceOf(YFAuthException.class);
    }

    @Test
    void throwsWhenCrumbEndpointFails() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

        assertThatThrownBy(() -> crumbStore.getCrumb()).isInstanceOf(YFAuthException.class);
    }

    @Test
    void toleratesCookieSeedFailure() throws Exception {
        HttpUrl base = server.url("/");
        EndpointConfig config = EndpointConfig.production().withHosts(base, base, deadUrl()).withUserAgent("test-agent/1.0");
        var store = new CrumbStore(YahooClientFactory.baseClient(config), config);
        server.enqueue(new MockResponse().setResponseCode(200).setBody("crumb-without-cookie"));

        assertThat(store.getCrumb()).isEqualTo(Crumb.of("crumb-without-cookie"));
    }

    @Test
    void tryGetCrumbIsEmptyWhenCrumbEndpointRateLimited() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

        assertThat(crumbStore.tryGetCrumb()).isEmpty();
    }

    @Test
    void tryGetCrumbIsEmptyOnIoFailure() throws Exception {
        HttpUrl base = server.url("/");
        EndpointConfig config = EndpointConfig.production().withHosts(deadUrl(), base, base).withUserAgent("test-agent/1.0");
        var store = new CrumbStore(YahooClientFactory.baseClient(config), config);
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(store.tryGetCrumb()).isEmpty();
    }

    @Test
    void tryGetCrumbStillThrowsOnInvalidCrumb() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("  "));

        assertThatThrownBy(() -> crumbStore.tryGetCrumb()).isInstanceOf(YFAuthException.class);
    }

    @Test
    void tryGetCrumbReturnsAndCachesCrumb() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok-crumb"));

        assertThat(crumbStore.tryGetCrumb()).contains(Crumb.of("ok-crumb"));
        assertThat(crumbStore.getCrumb()).isEqualTo(Crumb.of("ok-crumb"));
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    /** URL of a port with nothing listening, so connecting fails fast with an I/O error. */
    private static HttpUrl deadUrl() throws IOException {
        var dead = new MockWebServer();
        dead.start();
        HttpUrl url = dead.url("/");
        dead.shutdown();
        return url;
    }
}
