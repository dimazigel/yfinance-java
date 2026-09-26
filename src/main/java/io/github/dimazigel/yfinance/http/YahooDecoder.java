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
 * Jackson 3 decoding with the library's failure semantics: an empty 2xx body and malformed JSON are
 * {@link YFDataException}s. Feign wraps exceptions thrown here in a {@code DecodeException};
 * {@link YahooInvocationHandlerFactory} unwraps them again at the proxy boundary.
 */
final class YahooDecoder implements Decoder {

    private final Decoder delegate;

    YahooDecoder(JsonMapper mapper) {
        this.delegate = new Jackson3Decoder(mapper);
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        Object value;
        try {
            value = delegate.decode(response, type); // null for a missing or zero-length body
        } catch (JacksonException e) {
            throw new YFDataException("Yahoo Finance returned malformed JSON for " + path(response), e);
        }
        if (value == null) {
            throw new YFDataException("Yahoo Finance returned an empty body");
        }
        return value;
    }

    static String path(Response response) {
        return URI.create(response.request().url()).getRawPath();
    }
}
