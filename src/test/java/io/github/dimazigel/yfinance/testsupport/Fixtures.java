package io.github.dimazigel.yfinance.testsupport;

import io.github.dimazigel.yfinance.api.ChartApi;
import io.github.dimazigel.yfinance.api.FundamentalsApi;
import io.github.dimazigel.yfinance.api.LookupApi;
import io.github.dimazigel.yfinance.api.OptionsApi;
import io.github.dimazigel.yfinance.api.QuoteApi;
import io.github.dimazigel.yfinance.api.QuoteSummaryApi;
import io.github.dimazigel.yfinance.api.SearchApi;
import io.github.dimazigel.yfinance.api.YahooApis;
import io.github.dimazigel.yfinance.http.EndpointConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/** Shared helpers for fixture-driven Feign tests. */
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

    /** All seven interfaces against {@code server}, with optional OkHttp interceptors (e.g. to observe MDC). */
    public static YahooApis apis(MockWebServer server, okhttp3.Interceptor... interceptors) {
        var client = new okhttp3.OkHttpClient.Builder();
        for (var interceptor : interceptors) {
            client.addInterceptor(interceptor);
        }
        var base = server.url("/");
        return YahooApis.create(EndpointConfig.production().withHosts(base, base, base), client.build());
    }

    public static <T> T api(MockWebServer server, Class<T> apiClass) {
        YahooApis apis = apis(server);
        Object api;
        if (apiClass == ChartApi.class) api = apis.chart();
        else if (apiClass == QuoteSummaryApi.class) api = apis.quoteSummary();
        else if (apiClass == QuoteApi.class) api = apis.quote();
        else if (apiClass == FundamentalsApi.class) api = apis.fundamentals();
        else if (apiClass == OptionsApi.class) api = apis.options();
        else if (apiClass == SearchApi.class) api = apis.search();
        else if (apiClass == LookupApi.class) api = apis.lookup();
        else throw new IllegalArgumentException("Not a Yahoo API interface: " + apiClass);
        return apiClass.cast(api);
    }
}
