package io.github.dimazigel.yfinance.http;

import feign.Response;
import feign.codec.Decoder;
import feign.jackson3.Jackson3Decoder;
import io.github.dimazigel.yfinance.exception.YFAuthException;
import io.github.dimazigel.yfinance.exception.YFDataException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 decoding with the library's failure semantics: an empty 2xx body, malformed JSON, and an
 * I/O failure while the body is being read (headers already arrived, so {@link YahooFeignClient}
 * never sees it) are all {@link YFDataException}s, and an HTML page in place of JSON — Yahoo's EU
 * consent redirect or a block page, both served as 200 — is a {@link YFAuthException} that says so,
 * rather than a "malformed JSON" that sends the operator after Jackson. The body is read once into
 * memory for that check (Feign's OkHttp body is not repeatable) and handed to Jackson as bytes.
 * Feign wraps exceptions thrown here in a {@code DecodeException}; {@link YahooInvocationHandlerFactory}
 * unwraps them again at the proxy boundary.
 */
final class YahooDecoder implements Decoder {

    private final Decoder delegate;

    YahooDecoder(JsonMapper mapper) {
        this.delegate = new Jackson3Decoder(mapper);
    }

    @Override
    public Object decode(Response response, Type type) {
        byte[] bytes = readBody(response);
        if (bytes != null && looksLikeHtml(response, bytes)) {
            throw new YFAuthException("Yahoo returned an HTML page instead of JSON for " + path(response)
                    + " (consent required or access blocked)");
        }
        Response repeatable = bytes == null ? response : response.toBuilder().body(bytes).build();
        Object value;
        try {
            value = delegate.decode(repeatable, type); // null for a missing or zero-length body
        } catch (JacksonException e) {
            throw new YFDataException("Yahoo Finance returned malformed JSON for " + path(response), e);
        } catch (IOException e) {
            throw new YFDataException("I/O error calling Yahoo Finance", e);
        }
        if (value == null) {
            throw new YFDataException("Yahoo Finance returned an empty body");
        }
        return value;
    }

    /** The whole body, or {@code null} when there is none. */
    private static byte @Nullable [] readBody(Response response) {
        Response.Body body = response.body();
        if (body == null) {
            return null;
        }
        try (InputStream in = body.asInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            // A transport failure while the body streams (read timeout, reset, disconnect): headers
            // already arrived, so this happens here rather than in YahooFeignClient. Same contract either way.
            throw new YFDataException("I/O error calling Yahoo Finance", e);
        }
    }

    /** {@code Content-Type: text/html}, or a body whose first non-whitespace character opens a tag. */
    private static boolean looksLikeHtml(Response response, byte[] bytes) {
        Map<String, Collection<String>> headers = response.headers();
        Collection<String> contentTypes = headers.get("Content-Type");
        if (contentTypes != null) {
            for (String contentType : contentTypes) {
                if (contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
                    return true;
                }
            }
        }
        for (byte b : bytes) {
            if (!Character.isWhitespace(b)) {
                return b == '<';
            }
        }
        return false;
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
