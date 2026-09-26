package io.github.dimazigel.yfinance.http;

import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place Feign is configured. The builder runs on the library's OkHttp client (all resilience
 * lives in its interceptor chain), never retries on its own, decodes with Jackson 3, and maps every
 * failure to a {@link io.github.dimazigel.yfinance.exception.YFinanceException}. Request options mirror
 * the client's timeouts so {@code feign-okhttp} uses the configured client as is instead of cloning it.
 *
 * <p>Internal to the library — not API; public only because {@code api.YahooApis} lives in another
 * package. No Feign type appears in a public signature here, so referencing this class from consumer
 * code requires nothing beyond OkHttp on that code's compile classpath.
 */
public final class YahooFeign {

    private final Feign.Builder feign;

    private YahooFeign(Feign.Builder feign) {
        this.feign = feign;
    }

    /** Configures a {@code Feign.Builder} for {@code client}, creating its own Jackson 3 mapper. */
    public static YahooFeign of(OkHttpClient client) {
        JsonMapper mapper = YahooJsonMapper.create();
        return new YahooFeign(Feign.builder()
                .client(new YahooFeignClient(client))
                .options(new Request.Options(
                        client.connectTimeoutMillis(), TimeUnit.MILLISECONDS,
                        client.readTimeoutMillis(), TimeUnit.MILLISECONDS,
                        client.followRedirects()))
                .decoder(new YahooDecoder(mapper))
                .errorDecoder(new YahooErrorDecoder(mapper))
                .invocationHandlerFactory(new YahooInvocationHandlerFactory())
                .retryer(Retryer.NEVER_RETRY)
                .logLevel(Logger.Level.NONE));
    }

    /** Targets {@code api} at {@code base}. Feign joins {@code base + template} and templates start with {@code /}, so a trailing slash on {@code base} is stripped. */
    public <T> T target(Class<T> api, HttpUrl base) {
        String url = base.toString();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return feign.target(api, url);
    }
}
