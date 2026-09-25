package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import okhttp3.Cookie;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

class InMemoryCookieJarTest {

    @Test
    void mergesCookiesAndReplacesByIdentity() {
        var jar = new InMemoryCookieJar();
        HttpUrl url = HttpUrl.get("https://query1.finance.yahoo.com/");

        jar.saveFromResponse(url, List.of(cookie("A1", "first"), cookie("B", "kept")));
        jar.saveFromResponse(url, List.of(cookie("A1", "second")));

        assertThat(jar.loadForRequest(url))
                .extracting(Cookie::name, Cookie::value)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("A1", "second"),
                        org.assertj.core.groups.Tuple.tuple("B", "kept"));
    }

    @Test
    void dropsExpiredCookies() {
        var jar = new InMemoryCookieJar();
        HttpUrl url = HttpUrl.get("https://query1.finance.yahoo.com/");

        jar.saveFromResponse(url, List.of(new Cookie.Builder()
                .name("expired")
                .value("old")
                .domain("finance.yahoo.com")
                .path("/")
                .expiresAt(System.currentTimeMillis() - 1_000)
                .build()));

        assertThat(jar.loadForRequest(url)).isEmpty();
    }

    private static Cookie cookie(String name, String value) {
        return new Cookie.Builder()
                .name(name)
                .value(value)
                .domain("finance.yahoo.com")
                .path("/")
                .expiresAt(System.currentTimeMillis() + 60_000)
                .build();
    }
}
