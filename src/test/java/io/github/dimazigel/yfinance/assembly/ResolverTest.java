package io.github.dimazigel.yfinance.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dimazigel.yfinance.http.YahooObjectMapper;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResolverTest {

    private static final ObjectMapper JSON = YahooObjectMapper.create();
    private static final Symbol AAPL = Symbol.of("AAPL");

    private static JsonNode json(String s) throws Exception {
        return JSON.readTree(s);
    }

    @Test
    void parsesWirePaths() {
        assertThat(WirePath.parse("v7:marketCap")).isEqualTo(new WirePath(Source.V7, "marketCap", Optional.empty()));
        assertThat(WirePath.parse("qs:price.marketCap")).isEqualTo(new WirePath(Source.QUOTE_SUMMARY, "price.marketCap", Optional.empty()));
        assertThat(WirePath.parse("v7:dividendYield|PERCENT")).isEqualTo(new WirePath(Source.V7, "dividendYield", Optional.of(Unit.PERCENT)));
        assertThatThrownBy(() -> WirePath.parse("http:x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WirePath.parse("v7:x|FURLONGS")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void perPathUnitOverrideMakesBothSourcesAgree() throws Exception {   // Review Focus 4 (framework half)
        var spec = List.of(FieldSpec.optional("currentDividend.yield", Unit.RAW,
                "v7:dividendYield|PERCENT", "qs:summaryDetail.dividendYield"));
        var fromV7 = new Payload(AAPL, Optional.of(json("{\"dividendYield\": 0.32}")), Map.of());
        var fromQs = new Payload(AAPL, Optional.empty(), Map.of("summaryDetail", json("{\"dividendYield\": 0.0032}")));

        assertThat(Resolver.resolve(fromV7, spec).optDecimal("currentDividend.yield")).contains(new java.math.BigDecimal("0.0032"));
        assertThat(Resolver.resolve(fromQs, spec).optDecimal("currentDividend.yield")).contains(new java.math.BigDecimal("0.0032"));
    }

    @Test
    void v7WinsThenModulesInDeclaredOrder() throws Exception {
        var payload = new Payload(AAPL, Optional.of(json("{\"marketCap\": 100}")),
                Map.of("price", json("{\"marketCap\": 200}"), "summaryDetail", json("{\"marketCap\": 300}")));
        var specs = List.of(FieldSpec.required("marketCap", Unit.RAW,
                "v7:marketCap", "qs:price.marketCap", "qs:summaryDetail.marketCap"));

        assertThat(Resolver.resolve(payload, specs).decimal("marketCap")).isEqualByComparingTo("100");

        var noV7 = new Payload(AAPL, Optional.empty(), Map.of("summaryDetail", json("{\"marketCap\": 300}")));
        assertThat(Resolver.resolve(noV7, specs).decimal("marketCap")).isEqualByComparingTo("300");
    }

    @Test
    void resolvesRawFmtObjects() throws Exception {   // Review Focus 1
        var payload = new Payload(AAPL, Optional.empty(),
                Map.of("summaryDetail", json("{\"marketCap\": {\"raw\": 2950000000000, \"fmt\": \"2.95T\"},"
                        + "\"trailingPE\": {\"raw\": null, \"fmt\": null}, \"beta\": {}}")));
        var specs = List.of(
                FieldSpec.required("marketCap", Unit.RAW, "qs:summaryDetail.marketCap"),
                FieldSpec.optional("trailingPE", Unit.RAW, "qs:summaryDetail.trailingPE"),
                FieldSpec.optional("beta", Unit.RAW, "qs:summaryDetail.beta"));

        var r = Resolver.resolve(payload, specs);
        assertThat(r.decimal("marketCap")).isEqualByComparingTo("2950000000000");
        assertThat(r.optDecimal("trailingPE")).isEmpty();   // {raw:null} is absent
        assertThat(r.optDecimal("beta")).isEmpty();         // {} is absent
        assertThat(r.missingRequired()).isEmpty();
    }

    @Test
    void tracksMissingRequiredAndRejectsAccessToThem() throws Exception {
        var payload = new Payload(AAPL, Optional.of(json("{\"shortName\": \"Apple\", \"empty\": \"\", \"nul\": null}")), Map.of());
        var specs = List.of(
                FieldSpec.required("shortName", Unit.RAW, "v7:shortName"),
                FieldSpec.required("marketCap", Unit.RAW, "v7:marketCap", "qs:price.marketCap"),
                FieldSpec.required("empty", Unit.RAW, "v7:empty"),
                FieldSpec.required("nul", Unit.RAW, "v7:nul"),
                FieldSpec.optional("beta", Unit.RAW, "v7:beta"));

        var r = Resolver.resolve(payload, specs);
        assertThat(r.missingRequired()).containsExactly("marketCap", "empty", "nul");
        assertThat(r.has("shortName")).isTrue();
        assertThat(r.has("beta")).isFalse();
        assertThatThrownBy(() -> r.decimal("marketCap")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("marketCap");
    }

    @Test
    void appliesUnits() throws Exception {
        var payload = new Payload(AAPL, Optional.of(json("{\"pct\": 0.2083775, \"secs\": 1790347323, \"millis\": 345479400000,"
                + "\"day\": 1783296000, \"iso\": \"2023-09-30\", \"n\": 7, \"big\": 14594180000, \"flag\": true, \"txt\": \"x\"}")), Map.of());
        var specs = List.of(
                FieldSpec.required("pct", Unit.PERCENT, "v7:pct"),
                FieldSpec.required("secs", Unit.EPOCH_SECONDS, "v7:secs"),
                FieldSpec.required("millis", Unit.EPOCH_MILLIS, "v7:millis"),
                FieldSpec.required("day", Unit.EPOCH_DATE, "v7:day"),
                FieldSpec.required("iso", Unit.ISO_DATE, "v7:iso"),
                FieldSpec.required("n", Unit.RAW, "v7:n"),
                FieldSpec.required("big", Unit.RAW, "v7:big"),
                FieldSpec.required("flag", Unit.RAW, "v7:flag"),
                FieldSpec.required("txt", Unit.RAW, "v7:txt"));
        var r = Resolver.resolve(payload, specs);

        assertThat(r.decimal("pct")).isEqualByComparingTo("0.002083775");          // percent -> fraction
        assertThat(r.instant("secs")).isEqualTo(Instant.ofEpochSecond(1790347323));
        assertThat(r.instant("millis")).isEqualTo(Instant.ofEpochMilli(345479400000L));
        assertThat(r.date("day")).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(r.date("iso")).isEqualTo(LocalDate.of(2023, 9, 30));
        assertThat(r.intValue("n")).isEqualTo(7);
        assertThat(r.longValue("big")).isEqualTo(14_594_180_000L);
        assertThat(r.bool("flag")).isTrue();
        assertThat(r.string("txt")).isEqualTo("x");
    }

    @Test
    void clustersArePresentOnlyWhenEveryMemberIs() throws Exception {
        var payload = new Payload(AAPL, Optional.of(json("{\"a\": 1, \"b\": 2, \"c\": 3}")), Map.of());
        var specs = List.of(
                FieldSpec.clustered("full", "a", Unit.RAW, "v7:a"),
                FieldSpec.clustered("full", "b", Unit.RAW, "v7:b"),
                FieldSpec.clustered("partial", "c", Unit.RAW, "v7:c"),
                FieldSpec.clustered("partial", "d", Unit.RAW, "v7:d"));
        var r = Resolver.resolve(payload, specs);
        assertThat(r.clusterPresent("full")).isTrue();
        assertThat(r.clusterPresent("partial")).isFalse();
        assertThat(r.missingRequired()).isEmpty();   // clustered fields are optional
    }

    @Test
    void listsAndArrayIndexes() throws Exception {
        var payload = new Payload(AAPL, Optional.empty(), Map.of(
                "calendarEvents", json("{\"earnings\": {\"earningsDate\": [1793304000, 1793400000]}}"),
                "recommendationTrend", json("{\"trend\": [{\"period\": \"0m\"}, {\"period\": \"-1m\"}]}")));
        var specs = List.of(
                FieldSpec.required("nextEarnings.expected", Unit.EPOCH_SECONDS, "qs:calendarEvents.earnings.earningsDate.0"),
                FieldSpec.list("analysts.recommendationTrend", "qs:recommendationTrend.trend"),
                FieldSpec.list("analysts.secFilings", "qs:secFilings.filings"));
        var r = Resolver.resolve(payload, specs);
        assertThat(r.instant("nextEarnings.expected")).isEqualTo(Instant.ofEpochSecond(1793304000));
        assertThat(r.list("analysts.recommendationTrend")).hasSize(2);
        assertThat(r.list("analysts.secFilings")).isEmpty();       // absent list -> empty, never missing
        assertThat(r.missingRequired()).isEmpty();
    }
}
