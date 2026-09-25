package io.github.dimazigel.yfinance.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * A minimal process-wide cookie jar keyed by host. Sufficient for holding the Yahoo session
 * cookie ({@code A1}/{@code A3}) needed alongside the crumb. Not persisted.
 */
public final class InMemoryCookieJar implements CookieJar {

    private final Map<String, List<Cookie>> cookiesByHost = new ConcurrentHashMap<>();

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        long now = System.currentTimeMillis();
        var existing = new ArrayList<>(cookiesByHost.getOrDefault(url.host(), List.of()));
        existing.removeIf(cookie -> cookie.expiresAt() <= now);
        for (Cookie cookie : cookies) {
            existing.removeIf(saved -> sameIdentity(saved, cookie));
            if (cookie.expiresAt() > now) {
                existing.add(cookie);
            }
        }
        if (existing.isEmpty()) {
            cookiesByHost.remove(url.host());
        } else {
            cookiesByHost.put(url.host(), existing);
        }
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        long now = System.currentTimeMillis();
        var result = new ArrayList<Cookie>();
        cookiesByHost.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(cookie -> cookie.expiresAt() <= now);
            return entry.getValue().isEmpty();
        });
        cookiesByHost.forEach((host, cookies) -> {
            for (Cookie cookie : List.copyOf(cookies)) {
                if (cookie.matches(url)) {
                    result.add(cookie);
                }
            }
        });
        return result;
    }

    private static boolean sameIdentity(Cookie left, Cookie right) {
        return left.name().equals(right.name())
                && left.domain().equals(right.domain())
                && left.path().equals(right.path());
    }
}
