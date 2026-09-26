package io.github.dimazigel.yfinance.http;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Factory for the Jackson 3 {@link JsonMapper} used to deserialize Yahoo's JSON: java.time support is
 * built in, unknown properties and unknown enum values are tolerated, and {@code {raw, fmt}} numbers
 * are unwrapped by {@link Jackson3RawAwareNumberModule}.
 *
 * <p>Internal plumbing, public only because {@code api.YahooApis} lives in another package. Jackson
 * is an {@code implementation} dependency of this library, so referencing this class from consumer
 * code additionally requires Jackson on that code's compile classpath.
 */
public final class YahooJsonMapper {

    private YahooJsonMapper() {}

    public static JsonMapper create() {
        return JsonMapper.builder()
                .addModule(new Jackson3RawAwareNumberModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .build();
    }
}
