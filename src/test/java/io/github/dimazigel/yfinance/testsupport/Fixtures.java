package io.github.dimazigel.yfinance.testsupport;

import io.github.dimazigel.yfinance.http.SyncCallAdapterFactory;
import io.github.dimazigel.yfinance.http.YahooObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/** Shared helpers for fixture-driven Retrofit tests. */
public final class Fixtures {

    private Fixtures() {}

    public static String load(String name) {
        try (var in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static MockResponse jsonResponse(String fixtureName) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(load(fixtureName));
    }

    public static <T> T api(MockWebServer server, Class<T> apiClass) {
        return retrofit(server.url("/")).create(apiClass);
    }

    public static Retrofit retrofit(HttpUrl baseUrl) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addCallAdapterFactory(SyncCallAdapterFactory.create())
                .addConverterFactory(JacksonConverterFactory.create(YahooObjectMapper.create()))
                .build();
    }
}
