package io.github.dimazigel.yfinance.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Factory for the {@link ObjectMapper} used to deserialize Yahoo's JSON.
 *
 * <p>Internal plumbing, public only because {@code api.YahooApis} lives in another package. Jackson
 * is an {@code implementation} dependency of this library, so referencing this class from consumer
 * code additionally requires Jackson on that code's compile classpath.
 */
public final class YahooObjectMapper {

    private YahooObjectMapper() {}

    public static ObjectMapper create() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new RawAwareNumberModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    }
}
