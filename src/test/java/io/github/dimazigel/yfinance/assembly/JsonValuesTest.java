package io.github.dimazigel.yfinance.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.http.YahooJsonMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Pins {@link JsonValues}' "number or numeric string" decoding, shared by {@code Resolved} and {@code Nodes}. */
class JsonValuesTest {

    private static final JsonMapper JSON = YahooJsonMapper.create();

    private static JsonNode json(String body) throws Exception {
        return JSON.readTree(body);
    }

    @Test
    void toLongReadsANumberOrAStrippedNumericString() throws Exception {
        var node = json("{\"n\": 14594180000, \"s\": \" 14594180000 \", \"frac\": \"1.5\", \"junk\": \"abc\"}");

        assertThat(JsonValues.toLong(node.get("n"))).isEqualTo(14_594_180_000L);
        assertThat(JsonValues.toLong(node.get("s"))).isEqualTo(14_594_180_000L);
        assertThatThrownBy(() -> JsonValues.toLong(node.get("frac")))
                .as("a fractional string is not a valid long")
                .isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> JsonValues.toLong(node.get("junk"))).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void toIntReadsANumberOrAStrippedNumericString() throws Exception {
        var node = json("{\"n\": 7, \"s\": \" 34 \", \"frac\": \"1.5\", \"junk\": \"abc\"}");

        assertThat(JsonValues.toInt(node.get("n"))).isEqualTo(7);
        assertThat(JsonValues.toInt(node.get("s"))).isEqualTo(34);
        assertThatThrownBy(() -> JsonValues.toInt(node.get("frac")))
                .as("a fractional string is not a valid int")
                .isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> JsonValues.toInt(node.get("junk"))).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void toDecimalReadsANumberOrAStrippedNumericString() throws Exception {
        var node = json("{\"n\": 0.208377, \"s\": \" 0.208377 \", \"junk\": \"abc\"}");

        assertThat(JsonValues.toDecimal(node.get("n"))).isEqualByComparingTo(new BigDecimal("0.208377"));
        assertThat(JsonValues.toDecimal(node.get("s"))).isEqualByComparingTo(new BigDecimal("0.208377"));
        assertThatThrownBy(() -> JsonValues.toDecimal(node.get("junk"))).isInstanceOf(NumberFormatException.class);
    }
}
