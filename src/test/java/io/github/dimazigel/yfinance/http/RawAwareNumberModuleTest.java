package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Yahoo mixes plain numbers with {@code {"raw": n, "fmt": "..."}} objects, and occasionally sends
 * numbers as strings or blanks. Every numeric DTO field relies on this module to normalise them.
 */
class RawAwareNumberModuleTest {

    private final ObjectMapper mapper = YahooObjectMapper.create();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Numbers(@Nullable Long count, @Nullable Integer small, @Nullable BigDecimal price) {}

    @Test
    void plainScalarsDeserializeNormally() throws Exception {
        var n = mapper.readValue("{\"count\":52000000,\"small\":7,\"price\":190.5}", Numbers.class);
        assertThat(n.count()).isEqualTo(52_000_000L);
        assertThat(n.small()).isEqualTo(7);
        assertThat(n.price()).isEqualByComparingTo("190.5");
    }

    @Test
    void rawFmtObjectsAreUnwrapped() throws Exception {
        var n = mapper.readValue(
                "{\"count\":{\"raw\":52000000,\"fmt\":\"52M\"},\"small\":{\"raw\":7,\"fmt\":\"7\"},"
                        + "\"price\":{\"raw\":2950000000000,\"fmt\":\"2.95T\"}}", Numbers.class);
        assertThat(n.count()).isEqualTo(52_000_000L);
        assertThat(n.small()).isEqualTo(7);
        assertThat(n.price()).isEqualByComparingTo("2950000000000");
    }

    @Test
    void missingRawOrNullRawIsNull() throws Exception {
        var n = mapper.readValue(
                "{\"count\":{\"raw\":null,\"fmt\":\"N/A\"},\"small\":{\"fmt\":\"N/A\"},\"price\":{}}", Numbers.class);
        assertThat(n.count()).isNull();
        assertThat(n.small()).isNull();
        assertThat(n.price()).isNull();
    }

    @Test
    void nullAndBlankStringAreNull() throws Exception {
        var n = mapper.readValue("{\"count\":null,\"small\":\"\",\"price\":\"   \"}", Numbers.class);
        assertThat(n.count()).isNull();
        assertThat(n.small()).isNull();
        assertThat(n.price()).isNull();
    }

    @Test
    void numericStringsAreParsed() throws Exception {
        var n = mapper.readValue("{\"count\":\"42\",\"small\":\"3\",\"price\":\"1.25\"}", Numbers.class);
        assertThat(n.count()).isEqualTo(42L);
        assertThat(n.small()).isEqualTo(3);
        assertThat(n.price()).isEqualByComparingTo("1.25");
    }

    @Test
    void bigDecimalKeepsFullPrecision() throws Exception {
        var n = mapper.readValue("{\"price\":0.003125744}", Numbers.class);
        assertThat(n.price()).isEqualTo(new BigDecimal("0.003125744"));
    }

    @Test
    void nonNumericTextFailsLoudlyForDecimals() {
        // A garbage string must not silently become null or zero for money fields.
        assertThatThrownBy(() -> mapper.readValue("{\"price\":\"abc\"}", Numbers.class))
                .isInstanceOf(Exception.class);
    }
}
