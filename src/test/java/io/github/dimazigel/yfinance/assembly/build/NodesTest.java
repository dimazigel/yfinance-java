package io.github.dimazigel.yfinance.assembly.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.http.YahooJsonMapper;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NodesTest {

    private static final JsonMapper JSON = YahooJsonMapper.create();

    private static JsonNode json(String body) throws Exception {
        return JSON.readTree(body);
    }

    @Test
    void optLongAndOptIntParseStrictlyLikeResolved() throws Exception {   // final review, finding 11
        JsonNode node = json("{\"n\": 12, \"s\": \" 34 \", \"raw\": {\"raw\": 56, \"fmt\": \"56\"}, \"junk\": \"abc\"}");

        assertThat(Nodes.optLong(node, "n")).contains(12L);
        assertThat(Nodes.optLong(node, "s")).contains(34L);
        assertThat(Nodes.optLong(node, "raw")).contains(56L);
        assertThat(Nodes.optLong(node, "missing")).isEmpty();
        assertThat(Nodes.optInt(node, "n")).contains(12);
        assertThat(Nodes.optInt(node, "s")).contains(34);
        assertThatThrownBy(() -> Nodes.optLong(node, "junk")).as("never 0 for non-numeric text").isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> Nodes.optInt(node, "junk")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void optInstantSecondsParsesStrictlyLikeItsSiblings() throws Exception {   // final review, finding 2c
        JsonNode node = json("{\"n\": 1700000000, \"s\": \" 1700000000 \", \"junk\": \"abc\"}");

        assertThat(Nodes.optInstantSeconds(node, "n")).contains(Instant.ofEpochSecond(1700000000));
        assertThat(Nodes.optInstantSeconds(node, "s")).contains(Instant.ofEpochSecond(1700000000));
        assertThatThrownBy(() -> Nodes.optInstantSeconds(node, "junk"))
                .as("never epoch 0 for non-numeric text")
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void malformedUriAndIsoDateAreDroppedWithADebugLineNamingTheValue() throws Exception {   // final review, findings 6 and 10
        JsonNode node = json("{\"site\": \"http://exa mple.com/x\", \"day\": \"2026-13-45\", \"good\": \"2026-09-30\"}");
        try (var log = LogCapture.of(Nodes.class)) {
            assertThat(Nodes.optUri(node, "site")).isEmpty();
            assertThat(Nodes.optIsoDate(node, "day")).isEmpty();
            assertThat(Nodes.optIsoDate(node, "good")).contains(LocalDate.of(2026, 9, 30));

            assertThat(log.messages(Level.DEBUG)).hasSize(2)
                    .anySatisfy(m -> assertThat(m).contains("URI").contains("http://exa mple.com/x"))
                    .anySatisfy(m -> assertThat(m).contains("date").contains("2026-13-45"));
            assertThat(log.messages(Level.WARN)).isEmpty();
        }
    }
}
