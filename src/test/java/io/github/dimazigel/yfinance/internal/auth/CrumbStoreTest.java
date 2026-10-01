package io.github.dimazigel.yfinance.internal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.exception.YFAuthException;
import io.github.dimazigel.yfinance.http.AdaptiveRateLimitConfig;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.internal.http.YahooClientFactory;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
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
    private EndpointConfig config;
    private OkHttpClient client;
    private CrumbStore crumbStore;
    private final AtomicLong nowNanos = new AtomicLong();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        HttpUrl base = server.url("/");
        config = EndpointConfig.production().withHosts(base).withUserAgent("test-agent/1.0")
                .withCallTimeout(Duration.ofSeconds(2))
                .withAdaptiveRateLimit(AdaptiveRateLimitConfig.disabled());   // the store, not the limiter, is under test
        client = YahooClientFactory.baseClient(config);
        crumbStore = new CrumbStore(client, config);
    }

    /** A store whose clock the test drives, for the cooldown after a transient handshake failure. */
    private CrumbStore timedStore() {
        return new CrumbStore(client, config, nowNanos::get);
    }

    private void enqueueRateLimitedHandshake(String retryAfter) {
        server.enqueue(new MockResponse().setResponseCode(404));
        var limited = new MockResponse().setResponseCode(429).setBody("Too Many Requests");
        if (retryAfter != null) {
            limited.setHeader("Retry-After", retryAfter);
        }
        server.enqueue(limited);
    }

    private void enqueueHandshake(String crumb) {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(crumb));
    }

    /** What the consent entry point answers where no consent is required: some page without the form. */
    private static final MockResponse NO_CONSENT_WALL =
            new MockResponse().setResponseCode(200).setBody("<html><body>Yahoo home</body></html>");

    /** Yahoo's consent page, reduced to what the store reads; the two inputs differ in attribute order on purpose. */
    private static final String CONSENT_PAGE = "<html><body><form method=\"post\" class=\"consent-form\">"
            + "<input type=\"hidden\" name=\"csrfToken\" value=\"tok-123\">"
            + "<input value=\"sess-456\" name=\"sessionId\" type=\"hidden\">"
            + "<button type=\"submit\" name=\"agree\" value=\"agree\">Accept all</button></form></body></html>";

    private void advance(Duration by) {
        nowNanos.addAndGet(by.toNanos());
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
    void invalidateWithTheRejectedCrumbClearsOnlyThatCrumb() {
        enqueueHandshake("first-crumb");
        crumbStore.getCrumb();

        crumbStore.invalidate("some-older-crumb");   // another worker already refreshed: keep the current one
        assertThat(crumbStore.getCrumb()).isEqualTo(Crumb.of("first-crumb"));
        assertThat(server.getRequestCount()).as("no handshake for a stale rejection").isEqualTo(2);

        enqueueHandshake("second-crumb");
        crumbStore.invalidate("first-crumb");
        assertThat(crumbStore.getCrumb()).isEqualTo(Crumb.of("second-crumb"));
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    void invalidateWithoutARejectedCrumbKeepsTheCachedOne() {
        // The rejected request went out without a crumb (e.g. during a cooldown); whatever is cached
        // now was fetched since and is not the one Yahoo rejected.
        enqueueHandshake("first-crumb");
        crumbStore.getCrumb();

        crumbStore.invalidate(null);

        assertThat(crumbStore.getCrumb()).isEqualTo(Crumb.of("first-crumb"));
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void rateLimitedHandshakeEntersACooldownWithNoNetworkCallsAndOneWarning() {
        var store = timedStore();
        enqueueRateLimitedHandshake(null);

        try (var log = LogCapture.of(CrumbStore.class)) {
            assertThat(store.tryGetCrumb()).isEmpty();
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertThat(log.messages(Level.WARN)).singleElement().satisfies(m -> assertThat(m)
                    .contains("HTTP 429").contains("continuing without a crumb").contains("30000 ms"));

            advance(Duration.ofSeconds(29));
            assertThat(store.tryGetCrumb()).isEmpty();
            assertThat(server.getRequestCount()).as("no network call while cooling down").isEqualTo(2);
            assertThat(log.messages(Level.WARN)).as("WARN once, on entering the cooldown").hasSize(1);
            assertThat(log.messages(Level.DEBUG)).anySatisfy(m -> assertThat(m).contains("cooling down"));
        }

        advance(Duration.ofSeconds(2));   // 31 s: cooldown over
        enqueueHandshake("fresh");
        assertThat(store.tryGetCrumb()).contains(Crumb.of("fresh"));
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    void retryAfterExtendsTheCooldown() {
        var store = timedStore();
        enqueueRateLimitedHandshake("120");

        assertThat(store.tryGetCrumb()).isEmpty();
        advance(Duration.ofSeconds(100));
        assertThat(store.tryGetCrumb()).isEmpty();
        assertThat(server.getRequestCount()).as("Retry-After: 120 outlasts the 30 s backoff").isEqualTo(2);

        advance(Duration.ofSeconds(21));
        enqueueHandshake("fresh");
        assertThat(store.tryGetCrumb()).contains(Crumb.of("fresh"));
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    void consecutiveFailuresDoubleTheCooldownAndSuccessResetsIt() {
        var store = timedStore();
        enqueueRateLimitedHandshake(null);                  // failure 1: 30 s
        assertThat(store.tryGetCrumb()).isEmpty();

        advance(Duration.ofSeconds(31));
        enqueueRateLimitedHandshake(null);                  // failure 2: 60 s
        assertThat(store.tryGetCrumb()).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(4);

        advance(Duration.ofSeconds(59));
        assertThat(store.tryGetCrumb()).isEmpty();
        assertThat(server.getRequestCount()).as("still inside the doubled cooldown").isEqualTo(4);

        advance(Duration.ofSeconds(2));
        enqueueHandshake("fresh");                          // success resets the backoff
        assertThat(store.tryGetCrumb()).contains(Crumb.of("fresh"));

        store.invalidate("fresh");
        enqueueRateLimitedHandshake(null);                  // failure after a success: back to 30 s
        assertThat(store.tryGetCrumb()).isEmpty();
        advance(Duration.ofSeconds(31));
        enqueueHandshake("fresher");
        assertThat(store.tryGetCrumb()).contains(Crumb.of("fresher"));
        assertThat(server.getRequestCount()).isEqualTo(10);
    }

    @Test
    void aRejectedHandshakeAlsoStartsACooldown() {   // review recommendation: a blocked IP must not re-run the handshake per request
        var store = timedStore();
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("Forbidden"));
        server.enqueue(NO_CONSENT_WALL);

        try (var log = LogCapture.of(CrumbStore.class)) {
            assertThatThrownBy(store::tryGetCrumb).isInstanceOf(YFAuthException.class).hasMessageContaining("HTTP 403");
            assertThat(log.messages(Level.WARN)).singleElement().satisfies(m -> assertThat(m).contains("30000 ms"));

            advance(Duration.ofSeconds(29));
            assertThat(store.tryGetCrumb()).as("empty, no network, no throw while cooling down").isEmpty();
            assertThat(server.getRequestCount()).isEqualTo(3);
            assertThat(log.messages(Level.WARN)).hasSize(1);
        }

        advance(Duration.ofSeconds(2));
        enqueueHandshake("fresh");
        assertThat(store.tryGetCrumb()).contains(Crumb.of("fresh"));
        assertThat(server.getRequestCount()).isEqualTo(5);
    }

    @Test
    void getCrumbStillAttemptsDuringTheCooldown() {
        var store = timedStore();
        enqueueRateLimitedHandshake(null);
        assertThat(store.tryGetCrumb()).isEmpty();

        enqueueHandshake("ok-crumb");
        assertThat(store.getCrumb()).isEqualTo(Crumb.of("ok-crumb"));
        assertThat(server.getRequestCount()).isEqualTo(4);
        assertThat(store.tryGetCrumb()).contains(Crumb.of("ok-crumb"));
    }

    @Test
    void throwsWhenCrumbBlank() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("  "));
        server.enqueue(NO_CONSENT_WALL);

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
        EndpointConfig config = EndpointConfig.production().withHosts(base, base, base, deadUrl(), base).withUserAgent("test-agent/1.0");
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
        EndpointConfig config = EndpointConfig.production().withHosts(deadUrl(), base, base, base, base).withUserAgent("test-agent/1.0");
        var store = new CrumbStore(YahooClientFactory.baseClient(config), config);
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(store.tryGetCrumb()).isEmpty();
    }

    @Test
    void tryGetCrumbStillThrowsOnInvalidCrumb() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("  "));
        server.enqueue(NO_CONSENT_WALL);

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

    // --- EU consent fallback (Python yfinance's "csrf" cookie strategy) ---

    @Test
    void aConsentWallIsAcceptedAndTheCrumbRequestedAgain() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));                                    // cookie seed
        server.enqueue(new MockResponse().setResponseCode(200).setBody("<html>consent</html>"));   // crumb: a page, not a crumb
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "/v2/collectConsent?sessionId=sess-456"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(CONSENT_PAGE));
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Set-Cookie", "A3=consented; Path=/"));   // the POST
        server.enqueue(new MockResponse().setResponseCode(200));                                    // copyConsent
        server.enqueue(new MockResponse().setResponseCode(200).setBody("crumb-after-consent"));

        assertThat(crumbStore.getCrumb()).isEqualTo(Crumb.of("crumb-after-consent"));

        assertThat(server.getRequestCount()).isEqualTo(7);
        server.takeRequest();   // cookie seed
        assertThat(server.takeRequest().getPath()).isEqualTo("/v1/test/getcrumb");
        assertThat(server.takeRequest().getPath()).isEqualTo("/consent");
        assertThat(server.takeRequest().getPath()).isEqualTo("/v2/collectConsent?sessionId=sess-456");
        RecordedRequest post = server.takeRequest();
        assertThat(post.getMethod()).isEqualTo("POST");
        assertThat(post.getPath()).as("posted to the page the form was served from").isEqualTo("/v2/collectConsent?sessionId=sess-456");
        assertThat(post.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded");
        assertThat(post.getBody().readUtf8()).isEqualTo("agree=agree&agree=agree&consentUUID=default&sessionId=sess-456"
                + "&csrfToken=tok-123&originalDoneUrl=https%3A%2F%2Ffinance.yahoo.com%2F&namespace=yahoo");
        assertThat(server.takeRequest().getPath()).isEqualTo("/copyConsent?sessionId=sess-456");
        RecordedRequest crumbAgain = server.takeRequest();
        assertThat(crumbAgain.getPath()).isEqualTo("/v1/test/getcrumb");
        assertThat(crumbAgain.getHeader("Cookie")).as("the cookie the consent set travels with the crumb request").contains("A3=consented");
    }

    @Test
    void aRejectionWithoutAConsentWallIsStillTheOriginalFailure() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("Forbidden"));
        server.enqueue(NO_CONSENT_WALL);

        assertThatThrownBy(() -> crumbStore.getCrumb()).isInstanceOf(YFAuthException.class).hasMessageContaining("HTTP 403");
        assertThat(server.getRequestCount()).as("cookie, crumb, one look at the consent entry point").isEqualTo(3);
    }

    @Test
    void aCrumbStillRejectedAfterConsentThrows() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("<html>consent</html>"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(CONSENT_PAGE));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("<html>still blocked</html>"));

        assertThatThrownBy(() -> crumbStore.getCrumb()).isInstanceOf(YFAuthException.class)
                .hasMessageContaining("after accepting Yahoo's consent form");
        assertThat(server.getRequestCount()).isEqualTo(6);
    }

    @Test
    void anUnreachableConsentEntryPointKeepsTheOriginalFailure() throws Exception {
        HttpUrl base = server.url("/");
        EndpointConfig unreachable = config.withHosts(base, base, base, base, deadUrl());
        var store = new CrumbStore(YahooClientFactory.baseClient(unreachable), unreachable);
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("  "));

        assertThatThrownBy(store::getCrumb).isInstanceOf(YFAuthException.class).hasMessageContaining("empty or invalid crumb");
    }

    @Test
    void aRateLimitedCrumbRequestDoesNotTryTheConsentFlow() {
        enqueueRateLimitedHandshake(null);

        assertThat(crumbStore.tryGetCrumb()).isEmpty();
        assertThat(server.getRequestCount()).as("cookie and crumb only").isEqualTo(2);
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
