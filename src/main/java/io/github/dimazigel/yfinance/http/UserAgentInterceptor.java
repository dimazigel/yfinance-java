package io.github.dimazigel.yfinance.http;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

/** Adds a fixed {@code User-Agent} header to every request. */
public final class UserAgentInterceptor implements Interceptor {

    private final String userAgent;

    public UserAgentInterceptor(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        var request = chain.request().newBuilder().header("User-Agent", userAgent).build();
        return chain.proceed(request);
    }
}
