package io.github.dimazigel.yfinance.http;

import java.math.BigDecimal;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Yahoo's quoteSummary endpoint returns some numeric fields as plain scalars and others as
 * {@code {"raw": <number>, "fmt": "..."}} objects, even with {@code formatted=false}. This module
 * registers lenient deserializers for {@link Long}, {@link Integer} and {@link BigDecimal} that
 * accept either form (unwrapping {@code raw}), so DTOs can declare clean numeric types.
 */
public final class RawAwareNumberModule extends SimpleModule {

    public RawAwareNumberModule() {
        super("yahoo-raw-aware-numbers");
        addDeserializer(Long.class, new RawAware<>(JsonNode::asLong));
        addDeserializer(Integer.class, new RawAware<>(JsonNode::asInt));
        addDeserializer(BigDecimal.class, new RawAware<>(RawAwareNumberModule::toBigDecimal));
    }

    private static BigDecimal toBigDecimal(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText());
    }

    private static final class RawAware<T> extends ValueDeserializer<T> {
        private final Function<JsonNode, T> convert;

        private RawAware(Function<JsonNode, T> convert) {
            this.convert = convert;
        }

        @Override
        public @Nullable T deserialize(JsonParser p, DeserializationContext ctxt) {
            return fromNode(ctxt.readTree(p));
        }

        private @Nullable T fromNode(@Nullable JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isObject()) {
                JsonNode raw = node.get("raw");
                return raw == null || raw.isNull() ? null : convert.apply(raw);
            }
            if (node.isTextual() && node.asText().isBlank()) {
                return null;
            }
            return convert.apply(node);
        }
    }
}
