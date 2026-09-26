package io.github.dimazigel.yfinance.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries transient server errors (HTTP 500, 502, 503, 504) up to {@link RetryConfig#maxAttempts()}
 * times with exponential backoff, honouring a {@code Retry-After} header when present. Yahoo's
 * lookup endpoint in particular answers an HTML 500 page now and then; one retry is nearly always
 * enough. The final response, whatever it is, goes back to the caller.
 *
 * <p>Rate limiting (429) is deliberately not handled here: that belongs to
 * {@link AdaptiveRateLimitInterceptor}, which must sit <em>downstream</em> of this interceptor so
 * the retried request is paced like any other.
 */
public final class TransientErrorRetryInterceptor implements Interceptor {

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration delay) throws InterruptedException;
    }

    private static final Logger LOG = LoggerFactory.getLogger(TransientErrorRetryInterceptor.class);

    private final RetryConfig config;
    private final Sleeper sleeper;

    public TransientErrorRetryInterceptor(RetryConfig config) {
        this(config, delay -> Thread.sleep(delay.toMillis()));
    }

    TransientErrorRetryInterceptor(RetryConfig config, Sleeper sleeper) {
        this.config = config;
        this.sleeper = sleeper;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        for (int attempt = 1; ; attempt++) {
            Response response = chain.proceed(chain.request());
            if (!isTransient(response.code()) || attempt >= config.maxAttempts()) {
                return response;
            }
            Duration delay = delayBefore(attempt + 1, response.header("Retry-After"));
            LOG.atDebug()
                    .addKeyValue("status", response.code())
                    .addKeyValue("attempt", attempt + 1)
                    .addKeyValue("maxAttempts", config.maxAttempts())
                    .addKeyValue("delayMs", delay.toMillis())
                    .log("HTTP {} from Yahoo; retrying in {} ms (attempt {} of {})",
                            response.code(), delay.toMillis(), attempt + 1, config.maxAttempts());
            response.close();
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while waiting to retry a server error");
            }
        }
    }

    private static boolean isTransient(int code) {
        return code == 500 || code == 502 || code == 503 || code == 504;
    }

    /** The server's {@code Retry-After} (whole seconds) if given, else exponential backoff; capped. */
    private Duration delayBefore(int attempt, @Nullable String retryAfter) {
        Duration delay = parseRetryAfter(retryAfter);
        if (delay == null) {
            long factor = 1L << Math.min(attempt - 2, 30); // 1, 2, 4, ... for attempts 2, 3, 4, ...
            delay = config.initialDelay().multipliedBy(factor);
        }
        return delay.compareTo(config.maxDelay()) > 0 ? config.maxDelay() : delay;
    }

    private static @Nullable Duration parseRetryAfter(@Nullable String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.strip());
            return seconds > 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException e) {
            return null; // HTTP-date form: fall back to backoff
        }
    }
}
