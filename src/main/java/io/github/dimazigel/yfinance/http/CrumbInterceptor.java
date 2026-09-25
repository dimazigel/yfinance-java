package io.github.dimazigel.yfinance.http;

import io.github.dimazigel.yfinance.valueobject.Crumb;
import java.io.IOException;
import java.util.function.Supplier;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

/**
 * Appends the {@code crumb} query parameter to every outgoing request. When the supplier yields
 * {@code null} (no crumb available right now), the request proceeds without one.
 */
public final class CrumbInterceptor implements Interceptor {

    private final Supplier<@Nullable Crumb> crumbSupplier;

    public CrumbInterceptor(Supplier<@Nullable Crumb> crumbSupplier) {
        this.crumbSupplier = crumbSupplier;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        var original = chain.request();
        Crumb crumb = crumbSupplier.get();
        var url = original.url().newBuilder()
                .setQueryParameter("crumb", crumb == null ? null : crumb.value())
                .build();
        return chain.proceed(original.newBuilder().url(url).build());
    }
}
