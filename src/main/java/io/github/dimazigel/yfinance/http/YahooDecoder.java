package io.github.dimazigel.yfinance.http;

import feign.Response;
import feign.codec.Decoder;
import feign.jackson3.Jackson3Decoder;
import io.github.dimazigel.yfinance.exception.YFDataException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 decoding with the library's failure semantics: an empty 2xx body, malformed JSON, and an
 * I/O failure while the body is being streamed (headers already arrived, so {@link YahooFeignClient}
 * never sees it) are all {@link YFDataException}s. Feign wraps exceptions thrown here in a
 * {@code DecodeException}; {@link YahooInvocationHandlerFactory} unwraps them again at the proxy
 * boundary.
 */
final class YahooDecoder implements Decoder {

    private final Decoder delegate;

    YahooDecoder(JsonMapper mapper) {
        this.delegate = new Jackson3Decoder(mapper);
    }

    @Override
    public Object decode(Response response, Type type) {
        Object value;
        try {
            value = delegate.decode(response, type); // null for a missing or zero-length body
        } catch (JacksonException e) {
            throw new YFDataException("Yahoo Finance returned malformed JSON for " + path(response), e);
        } catch (IOException e) {
            // A transport failure while Jackson streams the body (read timeout, reset, disconnect): headers
            // already arrived, so this happens here rather than in YahooFeignClient. Same contract either way.
            throw new YFDataException("I/O error calling Yahoo Finance", e);
        }
        if (value == null) {
            throw new YFDataException("Yahoo Finance returned an empty body");
        }
        return value;
    }

    /**
     * The decoded request path (e.g. {@code /v8/finance/chart/EURUSD=X}), for messages and
     * {@link io.github.dimazigel.yfinance.exception.YFHttpException#path()}. Feign percent-encodes the
     * wire form (e.g. {@code EURUSD%3DX}); this undoes that for readability.
     */
    static String path(Response response) {
        return URI.create(response.request().url()).getPath();
    }
}
