package io.github.dimazigel.yfinance.http;

import feign.Response;
import feign.codec.ErrorDecoder;
import io.github.dimazigel.yfinance.exception.YFHttpException;
import io.github.dimazigel.yfinance.exception.YFRateLimitException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps every non-2xx response to the library's exceptions: 429 → {@link YFRateLimitException} with
 * {@code Retry-After}; anything else → {@link YFHttpException}. The message names the endpoint (path)
 * and appends Yahoo's error {@code description} when the body is its JSON envelope, a size summary
 * when it is an HTML error page, or the raw text otherwise.
 */
final class YahooErrorDecoder implements ErrorDecoder {

    private final JsonMapper mapper;

    YahooErrorDecoder(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        String path = YahooDecoder.path(response);
        String detail = errorDetail(response);
        if (response.status() == 429) {
            return new YFRateLimitException(
                    "Yahoo Finance rate limit hit (HTTP 429) for " + path + detail,
                    parseRetryAfter(first(response.headers().get("Retry-After"))));
        }
        return new YFHttpException(response.status(), path,
                "Yahoo Finance returned HTTP " + response.status() + " for " + path + detail);
    }

    private String errorDetail(Response response) {
        Response.Body body = response.body();
        if (body == null) {
            return "";
        }
        Charset charset = response.charset() != null ? response.charset() : StandardCharsets.UTF_8;
        try (var in = body.asInputStream()) {
            String text = new String(in.readAllBytes(), charset).strip();
            if (text.isEmpty()) {
                return "";
            }
            if (text.startsWith("<")) {
                return ": HTML error page (" + text.length() + " bytes)"; // Yahoo's 5xx pages; markup is noise
            }
            return ": " + yahooErrorDescription(text).orElse(text);
        } catch (IOException e) {
            return "";
        }
    }

    /** Extracts {@code description} from an envelope like {@code {"chart":{"error":{...}}}}. */
    private Optional<String> yahooErrorDescription(String text) {
        try {
            for (JsonNode envelope : mapper.readTree(text)) {
                String description = envelope.path("error").path("description").asString("");
                if (!description.isBlank()) {
                    return Optional.of(description);
                }
            }
        } catch (JacksonException e) {
            // Not JSON: fall back to the raw body.
        }
        return Optional.empty();
    }

    private static @Nullable String first(@Nullable Collection<String> values) {
        return values == null || values.isEmpty() ? null : values.iterator().next();
    }

    /** Parses a {@code Retry-After} header expressed as a whole number of seconds. */
    private static @Nullable Duration parseRetryAfter(@Nullable String headerValue) {
        if (headerValue == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(headerValue.strip()));
        } catch (NumberFormatException e) {
            return null; // HTTP-date form is not supported; treat as absent
        }
    }
}
