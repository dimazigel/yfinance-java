package io.github.dimazigel.yfinance.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import io.github.dimazigel.yfinance.dto.chart.ChartResponse;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the lenient drop-and-log branches of the package-private {@link MapperSupport} helpers. */
class MapperSupportTest {

    private static final Symbol AAPL = Symbol.of("AAPL");

    // ---- uri ----

    @Test
    void malformedUriIsDroppedWithADebugLineNamingTheValue() {
        try (var log = LogCapture.of(MapperSupport.class)) {
            assertThat(MapperSupport.uri("http://[bad")).isNull();

            assertThat(log.messages(Level.DEBUG)).hasSize(1)
                    .anySatisfy(m -> assertThat(m).contains("http://[bad"));
        }
    }

    @Test
    void nullOrBlankUriIsNullWithNoLogLine() {
        try (var log = LogCapture.of(MapperSupport.class)) {
            assertThat(MapperSupport.uri(null)).isNull();
            assertThat(MapperSupport.uri("  ")).isNull();

            assertThat(log.events()).isEmpty();
        }
    }

    // ---- zoneId ----

    @Test
    void unknownZoneIsDroppedWithADebugLineNamingTheValue() {
        try (var log = LogCapture.of(MapperSupport.class)) {
            assertThat(MapperSupport.zoneId("Mars/Olympus")).isNull();

            assertThat(log.messages(Level.DEBUG)).hasSize(1)
                    .anySatisfy(m -> assertThat(m).contains("Mars/Olympus"));
        }
    }

    @Test
    void nullZoneIsNullWithNoLogLine() {
        try (var log = LogCapture.of(MapperSupport.class)) {
            assertThat(MapperSupport.zoneId(null)).isNull();

            assertThat(log.events()).isEmpty();
        }
        assertThat(MapperSupport.zoneId("UTC")).isEqualTo(ZoneId.of("UTC"));
    }

    // ---- localDate ----

    @Test
    void unparseableDateIsDroppedWithADebugLineNamingTheValue() {
        try (var log = LogCapture.of(MapperSupport.class)) {
            assertThat(MapperSupport.localDate("2024-13-45")).isNull();

            assertThat(log.messages(Level.DEBUG)).hasSize(1)
                    .anySatisfy(m -> assertThat(m).contains("2024-13-45"));
        }
    }

    @Test
    void nullOrBlankDateIsNullWithNoLogLine() {
        try (var log = LogCapture.of(MapperSupport.class)) {
            assertThat(MapperSupport.localDate(null)).isNull();
            assertThat(MapperSupport.localDate(" ")).isNull();

            assertThat(log.events()).isEmpty();
        }
        assertThat(MapperSupport.localDate("2023-09-30")).isEqualTo(LocalDate.of(2023, 9, 30));
    }

    // ---- symbolOr ----

    @Test
    void symbolOrFallsBackOnNullOrBlank() {
        assertThat(MapperSupport.symbolOr(null, AAPL)).isEqualTo(AAPL);
        assertThat(MapperSupport.symbolOr("  ", AAPL)).isEqualTo(AAPL);
        assertThat(MapperSupport.symbolOr("brk-b", AAPL)).isEqualTo(Symbol.of("BRK-B"));
    }

    // ---- interval ----

    @Test
    void unknownIntervalIsDroppedWithADebugLineNamingTheValue() {
        try (var log = LogCapture.of(MapperSupport.class)) {
            assertThat(MapperSupport.interval("7m")).isNull();

            assertThat(log.messages(Level.DEBUG)).hasSize(1)
                    .anySatisfy(m -> assertThat(m).contains("7m"));
        }
    }

    @Test
    void nullOrBlankIntervalIsNullWithNoLogLine() {
        try (var log = LogCapture.of(MapperSupport.class)) {
            assertThat(MapperSupport.interval(null)).isNull();
            assertThat(MapperSupport.interval(" ")).isNull();

            assertThat(log.events()).isEmpty();
        }
        assertThat(MapperSupport.interval("1d")).isEqualTo(Interval.ONE_DAY);
    }

    // ---- ranges ----

    @Test
    void rangesDropsUnknownWireValuesKeepingKnownOnesInOrder() {
        try (var log = LogCapture.of(MapperSupport.class)) {
            var ranges = MapperSupport.ranges(List.of("1mo", "3y", "1y"));

            assertThat(ranges).containsExactly(Range.ONE_MONTH, Range.ONE_YEAR);
            assertThat(log.messages(Level.DEBUG)).hasSize(1)
                    .anySatisfy(m -> assertThat(m).contains("3y"));
        }
    }

    @Test
    void nullRangesIsEmptyWithNoLogLine() {
        try (var log = LogCapture.of(MapperSupport.class)) {
            assertThat(MapperSupport.ranges(null)).isEmpty();

            assertThat(log.events()).isEmpty();
        }
    }

    // ---- firstResult ----

    @Test
    void firstResultThrowsOnNullList() {
        assertThatThrownBy(() -> MapperSupport.firstResult(null, null, "chart data", AAPL))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("chart data")
                .hasMessageContaining(AAPL.toString());
    }

    @Test
    void firstResultThrowsOnEmptyList() {
        assertThatThrownBy(() -> MapperSupport.firstResult(List.of(), null, "chart data", AAPL))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining("chart data")
                .hasMessageContaining(AAPL.toString());
    }

    @Test
    void firstResultThrowsOnANonNullErrorNamingItsDescription() {
        var error = new ChartResponse.ChartError("Not Found", "No data found, symbol may be delisted");

        assertThatThrownBy(() -> MapperSupport.firstResult(List.of("x"), error, "chart data", AAPL))
                .isInstanceOf(YFDataException.class)
                .hasMessageContaining(AAPL.toString())
                .hasMessageContaining("No data found, symbol may be delisted");
    }

    @Test
    void firstResultReturnsTheFirstElementOfANonEmptyList() {
        assertThat(MapperSupport.firstResult(List.of("first", "second"), null, "chart data", AAPL))
                .isEqualTo("first");
    }

    // ---- describe ----

    @Test
    void describePrefersDescriptionOverCode() {
        var error = new ChartResponse.ChartError("404", "No data found, symbol may be delisted");
        assertThat(MapperSupport.describe(error)).isEqualTo("No data found, symbol may be delisted");
    }

    @Test
    void describeFallsBackToCodeWhenDescriptionIsBlank() {
        var error = new ChartResponse.ChartError("404", " ");
        assertThat(MapperSupport.describe(error)).isEqualTo("404");
    }

    @Test
    void describeFallsBackToToStringForANonYahooError() {
        Object error = "boom";
        assertThat(MapperSupport.describe(error)).isEqualTo("boom");
    }
}
