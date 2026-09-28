package io.github.dimazigel.yfinance.auth;

import io.github.dimazigel.yfinance.exception.YFAuthException;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.valueobject.Crumb;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs and caches Yahoo's cookie + crumb handshake.
 *
 * <p>The handshake is: (1) hit {@code fc.yahoo.com} to let Yahoo set a session cookie, then
 * (2) request a crumb from {@code /v1/test/getcrumb} (sent with that cookie). The crumb is then
 * attached to every authenticated data request. The result is cached until {@link #invalidate()}
 * or {@link #invalidate(String)}.
 *
 * <p>The cookie is best-effort: a failure to reach {@code fc.yahoo.com} (common behind SOCKS5 or
 * corporate proxies) does not abort the handshake. Use {@link #tryGetCrumb()} to also degrade on
 * transient crumb failures, since some endpoints (e.g. chart) work without a crumb.
 *
 * <p>Any failed handshake starts a <em>cooldown</em> during which {@link #tryGetCrumb()} returns
 * empty without touching the network: {@value #INITIAL_BACKOFF_SECONDS} s, doubling per
 * consecutive failure up to {@value #MAX_BACKOFF_MINUTES} min, or longer when a 429 carried a
 * {@code Retry-After}; a successful handshake resets it. A transient failure (HTTP 429 or an I/O
 * error) is swallowed by {@code tryGetCrumb()} on the call that meets it; a rejection (another error
 * status, a blank or HTML body — the "blocked" state) still throws {@link YFAuthException} from that
 * call, and only the calls during the cooldown come back empty. {@link #getCrumb()} always attempts.
 * Without the cooldown, every data request made while the crumb endpoint is rate-limiting or
 * blocking would re-run the two-request handshake against it.
 *
 * <p>TODO: the EU-consent (guce/collectConsent) CSRF cookie fallback that Python yfinance uses is
 * not yet implemented; only the {@code fc.yahoo.com} cookie strategy is attempted.
 */
public final class CrumbStore {

    private static final Logger LOG = LoggerFactory.getLogger(CrumbStore.class);
    private static final long INITIAL_BACKOFF_SECONDS = 30;
    private static final long MAX_BACKOFF_MINUTES = 5;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(INITIAL_BACKOFF_SECONDS);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(MAX_BACKOFF_MINUTES);

    private final OkHttpClient client;
    private final EndpointConfig config;
    private final LongSupplier nanoTime;
    private volatile @Nullable Crumb cached;
    /** Guarded by {@code this}: the cooldown deadline (not in the future when there is none). */
    private long cooldownUntilNanos;
    /** Guarded by {@code this}: failed handshakes since the last successful one. */
    private int consecutiveFailures;

    public CrumbStore(OkHttpClient client, EndpointConfig config) {
        this(client, config, System::nanoTime);
    }

    /** {@code nanoTime} is the monotonic clock the cooldown is measured on; injectable for tests. */
    CrumbStore(OkHttpClient client, EndpointConfig config, LongSupplier nanoTime) {
        this.client = client;
        this.config = config;
        this.nanoTime = nanoTime;
        this.cooldownUntilNanos = nanoTime.getAsLong();
    }

    /** The cached crumb, or a fresh handshake: this form always attempts, cooldown or not. */
    public Crumb getCrumb() {
        Crumb local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            Crumb current = cached;
            if (current == null) {
                current = handshake();
                cached = current;
            }
            return current;
        }
    }

    /**
     * Like {@link #getCrumb()}, but returns empty instead of throwing when the crumb endpoint fails
     * transiently (HTTP 429 or an I/O error), so the caller can proceed without a crumb and let the
     * target endpoint decide, and returns empty without a network call for the whole cooldown that
     * any failed handshake starts (see the class comment). A crumb Yahoo actually rejects (non-429
     * error status, blank or HTML body) still throws on the call that meets it.
     */
    public Optional<Crumb> tryGetCrumb() {
        Crumb local = cached;
        if (local != null) {
            return Optional.of(local);
        }
        synchronized (this) {
            Crumb current = cached;
            if (current != null) {
                return Optional.of(current);
            }
            long remainingMs = Duration.ofNanos(cooldownUntilNanos - nanoTime.getAsLong()).toMillis();
            if (remainingMs > 0) {
                LOG.atDebug()
                        .addKeyValue("cooldownMs", remainingMs)
                        .log("Crumb endpoint cooling down for another {} ms; continuing without a crumb", remainingMs);
                return Optional.empty();
            }
            try {
                current = handshake();
                cached = current;
                return Optional.of(current);
            } catch (TransientCrumbFailure e) {
                return Optional.empty();
            }
        }
    }

    /**
     * Drops the cached crumb unconditionally so the next {@link #getCrumb()} repeats the handshake.
     * Prefer {@link #invalidate(String)} when reacting to a rejected request.
     */
    public void invalidate() {
        synchronized (this) {
            if (cached != null) {
                LOG.atDebug().log("Crumb invalidated; next request will repeat the handshake");
            }
            cached = null;
        }
    }

    /**
     * Drops the cached crumb only if it is still {@code rejected}, the crumb Yahoo just answered
     * HTTP 401/403 to. When several requests are rejected at once, the first one's handshake
     * replaces the crumb and the others find it already refreshed instead of discarding it and
     * repeating the handshake each. {@code null} (the rejected request carried no crumb) never
     * invalidates: whatever is cached was fetched since.
     */
    public void invalidate(@Nullable String rejected) {
        synchronized (this) {
            Crumb current = cached;
            if (current == null || !current.value().equals(rejected)) {
                LOG.atDebug().log("Crumb already refreshed or none cached; keeping the current state");
                return;
            }
            cached = null;
            LOG.atDebug().log("Crumb invalidated; next request will repeat the handshake");
        }
    }

    /**
     * Runs the handshake and, when it fails for any reason, starts the cooldown (WARN once, here,
     * with its length) before rethrowing. Called under the store lock.
     */
    private Crumb handshake() {
        try {
            Crumb crumb = fetch();
            consecutiveFailures = 0;
            cooldownUntilNanos = nanoTime.getAsLong();
            return crumb;
        } catch (TransientCrumbFailure e) {
            long cooldownMs = startCooldown(e.retryAfter);
            LOG.atWarn()
                    .addKeyValue("cooldownMs", cooldownMs)
                    .log("{}; continuing without a crumb for the next {} ms", e.getMessage(), cooldownMs);
            throw e;
        } catch (YFAuthException e) {
            long cooldownMs = startCooldown(null);
            LOG.atWarn()
                    .addKeyValue("cooldownMs", cooldownMs)
                    .log("{}; no handshake will be attempted for the next {} ms", e.getMessage(), cooldownMs);
            throw e;
        }
    }

    private Crumb fetch() {
        seedCookie();
        var request = new Request.Builder().url(config.crumbUrl()).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 429) {
                throw new TransientCrumbFailure("Failed to obtain crumb: HTTP 429 from " + config.crumbUrl(), null,
                        retryAfterSeconds(response.header("Retry-After")));
            }
            if (!response.isSuccessful()) {
                throw new YFAuthException(
                        "Failed to obtain crumb: HTTP " + response.code() + " from " + config.crumbUrl());
            }
            var body = response.body();
            String crumb = body == null ? null : body.string();
            if (crumb == null || crumb.isBlank() || crumb.contains("<html")) {
                throw new YFAuthException("Yahoo returned an empty or invalid crumb");
            }
            LOG.atDebug().log("Obtained Yahoo crumb");
            return Crumb.of(crumb.strip());
        } catch (IOException e) {
            throw new TransientCrumbFailure("I/O error while obtaining crumb", e, null);
        }
    }

    /** Records one more failed handshake and extends the cooldown accordingly; returns its length in ms. */
    private long startCooldown(@Nullable Duration retryAfter) {
        consecutiveFailures++;
        long backoffNanos = Math.min(MAX_BACKOFF.toNanos(), INITIAL_BACKOFF.toNanos() << Math.min(consecutiveFailures - 1, 10));
        long cooldownNanos = Math.max(backoffNanos, retryAfter == null ? 0L : retryAfter.toNanos());
        cooldownUntilNanos = nanoTime.getAsLong() + cooldownNanos;
        return Duration.ofNanos(cooldownNanos).toMillis();
    }

    private static @Nullable Duration retryAfterSeconds(@Nullable String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.strip());
            return seconds > 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException e) {
            return null; // HTTP-date form: the backoff alone applies
        }
    }

    /** Hits {@code fc.yahoo.com} purely so Yahoo sets the session cookie; failures are tolerated. */
    private void seedCookie() {
        var request = new Request.Builder().url(config.cookieUrl()).get().build();
        try (Response response = client.newCall(request).execute()) {
            response.body(); // drain; status (often 404) is irrelevant, the Set-Cookie matters
        } catch (IOException e) {
            // Non-critical: the crumb (and chart API) can still work without this cookie.
            LOG.atWarn()
                    .addKeyValue("cause", e.toString())
                    .log("Cookie fetch from {} failed; continuing without it", config.cookieUrl());
        }
    }

    /** A crumb failure worth degrading on (rate limit or I/O) rather than an invalid crumb. */
    private static final class TransientCrumbFailure extends YFAuthException {
        private static final long serialVersionUID = 1L;

        /** The 429's {@code Retry-After}, when it carried one. */
        private final transient @Nullable Duration retryAfter;

        TransientCrumbFailure(String message, @Nullable Throwable cause, @Nullable Duration retryAfter) {
            super(message, cause);
            this.retryAfter = retryAfter;
        }
    }
}
