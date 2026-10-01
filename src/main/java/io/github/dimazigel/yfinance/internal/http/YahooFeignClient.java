package io.github.dimazigel.yfinance.internal.http;

import feign.Client;
import feign.Request;
import feign.Response;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFRateLimitException;
import java.io.IOException;
import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * Runs Feign requests on the library's OkHttp client and turns transport failures into
 * {@link YFDataException} — except the limiter's fail-fast ({@link RateLimitBudgetExceeded}: the
 * paced wait could not fit the call timeout), which is a rate-limit condition and surfaces as a
 * {@link YFRateLimitException} whose {@code retryAfter} is the wait the limiter wanted. Feign's
 * method handler only catches {@link IOException} (to consult its retryer), so the unchecked
 * exceptions thrown here reach the caller untouched — which is what keeps the OkHttp interceptors
 * the single retry path.
 */
final class YahooFeignClient implements Client {

    private final Client delegate;

    YahooFeignClient(okhttp3.OkHttpClient client) {
        this.delegate = new feign.okhttp.OkHttpClient(client);
    }

    @Override
    public Response execute(Request request, Request.Options options) {
        try {
            return delegate.execute(request, options);
        } catch (IOException e) {
            RateLimitBudgetExceeded budget = budgetExceeded(e);
            if (budget != null) {
                throw new YFRateLimitException("Rate-limit pacing of " + budget.pendingWait().toMillis()
                        + " ms exceeds the remaining call timeout for " + URI.create(request.url()).getPath(),
                        budget.pendingWait());
            }
            throw new YFDataException("I/O error calling Yahoo Finance", e);
        }
    }

    /** The limiter's fail-fast, if it is what {@code e} is or wraps (OkHttp may re-wrap on the way out). */
    private static @Nullable RateLimitBudgetExceeded budgetExceeded(Throwable e) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof RateLimitBudgetExceeded budget) {
                return budget;
            }
            current = current.getCause();
        }
        return null;
    }
}
