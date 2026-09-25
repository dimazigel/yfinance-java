package io.github.dimazigel.yfinance.http;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFRateLimitException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * A Retrofit {@link CallAdapter.Factory} that lets API methods declare the DTO body as their return
 * type (e.g. {@code ChartResponse chart(...)}) instead of {@code Call<ChartResponse>}. The adapter
 * executes the call synchronously and translates transport-level failures into yfinance exceptions,
 * so services receive a ready-to-map DTO.
 */
public final class SyncCallAdapterFactory extends CallAdapter.Factory {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static SyncCallAdapterFactory create() {
        return new SyncCallAdapterFactory();
    }

    @Override
    public @Nullable CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
        // Leave raw Call<T> return types to Retrofit's default adapter.
        if (getRawType(returnType) == Call.class) {
            return null;
        }
        return new CallAdapter<Object, Object>() {
            @Override
            public Type responseType() {
                return returnType;
            }

            @Override
            public Object adapt(Call<Object> call) {
                return execute(call);
            }
        };
    }

    private static Object execute(Call<Object> call) {
        Response<Object> response;
        try {
            response = call.execute();
        } catch (IOException e) {
            throw new YFDataException("I/O error calling Yahoo Finance", e);
        }
        String path = call.request().url().encodedPath(); // names the endpoint and symbol in errors
        if (response.code() == 429) {
            throw new YFRateLimitException(
                    "Yahoo Finance rate limit hit (HTTP 429) for " + path + errorDetail(response),
                    parseRetryAfter(response.headers().get("Retry-After")));
        }
        if (!response.isSuccessful()) {
            throw new YFDataException(
                    "Yahoo Finance returned HTTP " + response.code() + " for " + path + errorDetail(response));
        }
        Object body = response.body();
        if (body == null) {
            throw new YFDataException("Yahoo Finance returned an empty body");
        }
        return body;
    }

    /**
     * Reads the (already-buffered) error body for a clearer message; tolerant of read failures. When
     * the body is Yahoo's error envelope, only its explanation is kept.
     */
    private static String errorDetail(Response<?> response) {
        try (var errorBody = response.errorBody()) {
            if (errorBody == null) {
                return "";
            }
            String text = errorBody.string().strip();
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
    private static Optional<String> yahooErrorDescription(String body) {
        try {
            for (JsonNode envelope : JSON.readTree(body)) {
                String description = envelope.path("error").path("description").asText("");
                if (!description.isBlank()) {
                    return Optional.of(description);
                }
            }
        } catch (JacksonException e) {
            // Not JSON (e.g. an HTML error page): fall back to the raw body.
        }
        return Optional.empty();
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
