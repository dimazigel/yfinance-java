package io.github.dimazigel.yfinance.http;

import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One DEBUG line per physical HTTP attempt: method, path and query, status, body size and duration
 * — the trail needed to answer "what did the library actually send, and what came back?". Sits
 * below the retry interceptors (so every attempt is logged) and above {@link CrumbInterceptor} (so
 * the crumb is not in the URL yet); the {@code crumb} parameter is masked regardless, in case the
 * chain is ever reordered.
 */
public final class RequestLogInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLogInterceptor.class);

    @Override
    public Response intercept(Chain chain) throws IOException {
        var request = chain.request();
        String target = describe(request.url());
        long startedAt = System.nanoTime();
        Response response;
        try {
            response = chain.proceed(request);
        } catch (IOException e) {
            long ms = elapsedMillis(startedAt);
            LOG.atDebug()
                    .addKeyValue("durationMs", ms)
                    .log("{} {} failed after {} ms: {}", request.method(), target, ms, e.toString());
            throw e;
        }
        long ms = elapsedMillis(startedAt);
        long bytes = response.body() != null ? response.body().contentLength() : -1;
        var event = LOG.atDebug().addKeyValue("status", response.code()).addKeyValue("durationMs", ms);
        if (bytes >= 0) {
            event = event.addKeyValue("bytes", bytes);
        }
        event.log("{} {} -> {}{} in {} ms",
                request.method(), target, response.code(), bytes >= 0 ? " (" + bytes + " B)" : "", ms);
        return response;
    }

    /** Path plus query with the crumb masked; nothing else in a Yahoo URL is sensitive. */
    static String describe(HttpUrl url) {
        var masked = url.newBuilder();
        if (url.queryParameter("crumb") != null) {
            masked.setQueryParameter("crumb", "***");
        }
        HttpUrl safe = masked.build();
        String query = safe.encodedQuery();
        return query == null ? safe.encodedPath() : safe.encodedPath() + "?" + query;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
