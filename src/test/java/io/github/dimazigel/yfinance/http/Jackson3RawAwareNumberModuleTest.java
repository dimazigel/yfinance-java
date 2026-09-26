package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Jackson 3 port of the {@code {raw, fmt}} number normalisation; same cases as the Jackson 2 module. */
class Jackson3RawAwareNumberModuleTest {

    private final JsonMapper mapper = YahooJsonMapper.create();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Numbers(@Nullable Long count, @Nullable Integer small, @Nullable BigDecimal price) {}

    @Test
    void plainScalarsDeserializeNormally() {
        var n = mapper.readValue("{\"count\":52000000,\"small\":7,\"price\":190.5}", Numbers.class);
        assertThat(n.count()).isEqualTo(52_000_000L);
        assertThat(n.small()).isEqualTo(7);
        assertThat(n.price()).isEqualByComparingTo("190.5");
    }

    @Test
    void rawFmtObjectsAreUnwrapped() {
        var n = mapper.readValue(
                "{\"count\":{\"raw\":52000000,\"fmt\":\"52M\"},\"small\":{\"raw\":7,\"fmt\":\"7\"},"
                        + "\"price\":{\"raw\":2950000000000,\"fmt\":\"2.95T\"}}", Numbers.class);
        assertThat(n.count()).isEqualTo(52_000_000L);
        assertThat(n.small()).isEqualTo(7);
        assertThat(n.price()).isEqualByComparingTo("2950000000000");
    }

    @Test
    void missingRawOrNullRawIsNull() {
        var n = mapper.readValue(
                "{\"count\":{\"raw\":null,\"fmt\":\"N/A\"},\"small\":{\"fmt\":\"N/A\"},\"price\":{}}", Numbers.class);
        assertThat(n.count()).isNull();
        assertThat(n.small()).isNull();
        assertThat(n.price()).isNull();
    }

    @Test
    void nullAndBlankStringAreNull() {
        var n = mapper.readValue("{\"count\":null,\"small\":\"\",\"price\":\"   \"}", Numbers.class);
        assertThat(n.count()).isNull();
        assertThat(n.small()).isNull();
        assertThat(n.price()).isNull();
    }

    @Test
    void numericStringsAreParsed() {
        var n = mapper.readValue("{\"count\":\"42\",\"small\":\"3\",\"price\":\"1.25\"}", Numbers.class);
        assertThat(n.count()).isEqualTo(42L);
        assertThat(n.small()).isEqualTo(3);
        assertThat(n.price()).isEqualByComparingTo("1.25");
    }

    @Test
    void bigDecimalKeepsFullPrecision() {
        var n = mapper.readValue("{\"price\":0.003125744}", Numbers.class);
        assertThat(n.price()).isEqualTo(new BigDecimal("0.003125744"));
    }

    @Test
    void nonNumericTextFailsLoudlyForDecimals() {
        assertThatThrownBy(() -> mapper.readValue("{\"price\":\"abc\"}", Numbers.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void unknownPropertiesAndUnknownEnumsAreTolerated() {
        record WithEnum(@Nullable DayOfWeek day) {}
        assertThat(mapper.readValue("{\"count\":1,\"surprise\":true}", Numbers.class).count()).isEqualTo(1L);
        assertThat(mapper.readValue("{\"day\":\"FUNDAY\"}", WithEnum.class).day()).isNull();
    }
}
