package io.github.dimazigel.yfinance.http;

import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place Feign is configured. The builder runs on the library's OkHttp client (all resilience
 * lives in its interceptor chain), never retries on its own, decodes with Jackson 3, and maps every
 * failure to a {@link io.github.dimazigel.yfinance.exception.YFinanceException}. Request options mirror
 * the client's timeouts so {@code feign-okhttp} uses the configured client as is instead of cloning it.
 *
 * <p>Internal to the library — not API; may change without notice. {@code public} only because
 * {@code api.YahooApis} lives in another package; Feign is an {@code implementation} dependency of
 * this library, so referencing this class from consumer code requires Feign on that code's compile
 * classpath.
 */
public final class YahooFeign {

    private YahooFeign() {}

    public static Feign.Builder builder(OkHttpClient client, JsonMapper mapper) {
        return Feign.builder()
                .client(new YahooFeignClient(client))
                .options(new Request.Options(
                        client.connectTimeoutMillis(), TimeUnit.MILLISECONDS,
                        client.readTimeoutMillis(), TimeUnit.MILLISECONDS,
                        client.followRedirects()))
                .decoder(new YahooDecoder(mapper))
                .errorDecoder(new YahooErrorDecoder(mapper))
                .invocationHandlerFactory(new YahooInvocationHandlerFactory())
                .retryer(Retryer.NEVER_RETRY)
                .logLevel(Logger.Level.NONE);
    }
}
