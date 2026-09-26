package io.github.dimazigel.yfinance.http;

import feign.Client;
import feign.Request;
import feign.Response;
import io.github.dimazigel.yfinance.exception.YFDataException;
import java.io.IOException;

/**
 * Runs Feign requests on the library's OkHttp client and turns transport failures into
 * {@link YFDataException}. Feign's method handler only catches {@link IOException} (to consult its
 * retryer), so the unchecked exception thrown here reaches the caller untouched — which is what keeps
 * the OkHttp interceptors the single retry path.
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
            throw new YFDataException("I/O error calling Yahoo Finance", e);
        }
    }
}
