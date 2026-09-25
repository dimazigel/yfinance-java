package io.ziggy.yfinance.auth;

import io.ziggy.yfinance.exception.YFAuthException;
import io.ziggy.yfinance.http.EndpointConfig;
import io.ziggy.yfinance.valueobject.Crumb;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.lang.System.Logger;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

/**
 * Performs and caches Yahoo's cookie + crumb handshake.
 *
 * <p>The handshake is: (1) hit {@code fc.yahoo.com} to let Yahoo set a session cookie, then
 * (2) request a crumb from {@code /v1/test/getcrumb} (sent with that cookie). The crumb is then
 * attached to every authenticated data request. The result is cached until {@link #invalidate()}.
 *
 * <p>The cookie is best-effort: a failure to reach {@code fc.yahoo.com} (common behind SOCKS5 or
 * corporate proxies) does not abort the handshake. Use {@link #tryGetCrumb()} to also degrade on
 * transient crumb failures, since some endpoints (e.g. chart) work without a crumb.
 *
 * <p>TODO: the EU-consent (guce/collectConsent) CSRF cookie fallback that Python yfinance uses is
 * not yet implemented; only the {@code fc.yahoo.com} cookie strategy is attempted.
 */
public final class CrumbStore {

    private static final Logger LOG = System.getLogger(CrumbStore.class.getName());

    private final OkHttpClient client;
    private final EndpointConfig config;
    private volatile @Nullable Crumb cached;

    public CrumbStore(OkHttpClient client, EndpointConfig config) {
        this.client = client;
        this.config = config;
    }

    public Crumb getCrumb() {
        Crumb local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached == null) {
                cached = fetch();
            }
            return cached;
        }
    }

    /**
     * Like {@link #getCrumb()}, but returns empty instead of throwing when the crumb endpoint fails
     * transiently (HTTP 429 or an I/O error), so the caller can proceed without a crumb and let the
     * target endpoint decide. Failures are not cached; the next call retries the handshake. A crumb
     * Yahoo actually rejects (non-429 error status, blank or HTML body) still throws.
     */
    public Optional<Crumb> tryGetCrumb() {
        try {
            return Optional.of(getCrumb());
        } catch (TransientCrumbFailure e) {
            LOG.log(Level.WARNING, "{0}; continuing without a crumb", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Drops the cached crumb so the next {@link #getCrumb()} repeats the handshake. Call this when
     * Yahoo rejects the crumb (HTTP 401/403) so a long-running client can recover from rotation.
     */
    public void invalidate() {
        synchronized (this) {
            if (cached != null) {
                LOG.log(Level.DEBUG, "Crumb invalidated; next request will repeat the handshake");
            }
            cached = null;
        }
    }

    private Crumb fetch() {
        seedCookie();
        var request = new Request.Builder().url(config.crumbUrl()).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 429) {
                throw new TransientCrumbFailure("Failed to obtain crumb: HTTP 429 from " + config.crumbUrl(), null);
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
            LOG.log(Level.DEBUG, "Obtained Yahoo crumb");
            return Crumb.of(crumb.strip());
        } catch (IOException e) {
            throw new TransientCrumbFailure("I/O error while obtaining crumb", e);
        }
    }

    /** Hits {@code fc.yahoo.com} purely so Yahoo sets the session cookie; failures are tolerated. */
    private void seedCookie() {
        var request = new Request.Builder().url(config.cookieUrl()).get().build();
        try (Response response = client.newCall(request).execute()) {
            response.body(); // drain; status (often 404) is irrelevant, the Set-Cookie matters
        } catch (IOException e) {
            // Non-critical: the crumb (and chart API) can still work without this cookie.
            LOG.log(Level.WARNING, "Cookie fetch from {0} failed ({1}); continuing without it",
                    config.cookieUrl(), e.toString());
        }
    }

    /** A crumb failure worth degrading on (rate limit or I/O) rather than an invalid crumb. */
    private static final class TransientCrumbFailure extends YFAuthException {
        TransientCrumbFailure(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }
}
