package io.github.dimazigel.yfinance.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.enums.EventType;
import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.service.HistoryRequest;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HistoryQueryTest {

    @Test
    void builderDefaultsIncludePrePostFalseAndAllEvents() {
        var query = HistoryQuery.of(Interval.ONE_DAY).range(Range.ONE_MONTH).build();

        assertThat(query.interval()).isEqualTo(Interval.ONE_DAY);
        assertThat(query.includePrePost()).isFalse();
        assertThat(query.events()).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(EventType.class));
        assertThat(query.range()).contains(Range.ONE_MONTH);
        assertThat(query.hasPeriod()).isFalse();
    }

    @Test
    void neitherRangeNorPeriodRejected() {
        assertThatThrownBy(() -> HistoryQuery.of(Interval.ONE_DAY).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangeAndPeriodAreMutuallyExclusive() {
        // Setting one after the other clears the first, rather than allowing both at once.
        var query = HistoryQuery.of(Interval.ONE_DAY)
                .range(Range.ONE_MONTH)
                .period(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000))
                .build();

        assertThat(query.range()).isEmpty();
        assertThat(query.start()).contains(Instant.ofEpochSecond(1000));
        assertThat(query.hasPeriod()).isTrue();

        var backToRange = HistoryQuery.of(Interval.ONE_DAY)
                .period(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000))
                .range(Range.ONE_MONTH)
                .build();

        assertThat(backToRange.start()).isEmpty();
        assertThat(backToRange.end()).isEmpty();
        assertThat(backToRange.range()).contains(Range.ONE_MONTH);
    }

    @Test
    void bothRangeAndPeriodPresentRejectedAtConstruction() {
        assertThatThrownBy(() -> new HistoryQuery(Interval.ONE_DAY, Optional.of(Range.ONE_MONTH),
                Optional.of(Instant.ofEpochSecond(1000)), Optional.empty(), false, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEndNotAfterStart() {
        var t = Instant.ofEpochSecond(2000);
        assertThatThrownBy(() -> HistoryQuery.of(Interval.ONE_DAY).period(t, Instant.ofEpochSecond(1000)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end");
        assertThatThrownBy(() -> HistoryQuery.of(Interval.ONE_DAY).period(t, t).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openEndedPeriodLeavesEndAbsent() {
        var query = HistoryQuery.of(Interval.ONE_DAY).period(Instant.ofEpochSecond(1000)).build();

        assertThat(query.hasPeriod()).isTrue();
        assertThat(query.start()).contains(Instant.ofEpochSecond(1000));
        assertThat(query.end()).isEmpty();
    }

    @Test
    void orderedPeriodIsAccepted() {
        assertThatCode(() -> HistoryQuery.of(Interval.ONE_DAY)
                        .period(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1001)).build())
                .doesNotThrowAnyException();
    }

    @Test
    void eventsAreCopiedAndImmutable() {
        var mutable = EnumSet.of(EventType.DIVIDENDS);
        var query = HistoryQuery.of(Interval.ONE_DAY).range(Range.ONE_MONTH).events(mutable).build();
        mutable.add(EventType.SPLITS);

        assertThat(query.events()).containsExactly(EventType.DIVIDENDS);
        assertThatThrownBy(() -> query.events().add(EventType.CAPITAL_GAINS))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyEventsVarargsMeansNoEvents() {
        var query = HistoryQuery.of(Interval.ONE_DAY).range(Range.ONE_MONTH).events().build();

        assertThat(query.events()).isEmpty();
    }

    @Test
    void eventsVarargsCollectsGivenTypes() {
        var query = HistoryQuery.of(Interval.ONE_DAY).range(Range.ONE_MONTH)
                .events(EventType.DIVIDENDS, EventType.SPLITS).build();

        assertThat(query.events()).containsExactlyInAnyOrder(EventType.DIVIDENDS, EventType.SPLITS);
    }

    @Test
    void staticRangeAndPeriodFactoriesMatchTheBuilder() {
        assertThat(HistoryQuery.range(Range.ONE_MONTH, Interval.ONE_DAY))
                .isEqualTo(HistoryQuery.of(Interval.ONE_DAY).range(Range.ONE_MONTH).build());
        assertThat(HistoryQuery.period(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000), Interval.ONE_HOUR))
                .isEqualTo(HistoryQuery.of(Interval.ONE_HOUR)
                        .period(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000)).build());
    }

    @Test
    void toStringIsSane() {
        var query = HistoryQuery.of(Interval.ONE_DAY).range(Range.ONE_MONTH).build();

        assertThat(query.toString()).contains("ONE_DAY").contains("ONE_MONTH");
    }

    @Test
    @SuppressWarnings({"deprecation", "removal"}) // HistoryRequest is the deprecated adapter under test here
    void historyRequestToQueryEquivalenceForRangeForm() {
        var request = HistoryRequest.builder(Symbol.of("AAPL"))
                .interval(Interval.ONE_WEEK)
                .range(Range.SIX_MONTHS)
                .includePrePost(true)
                .events(Set.of(EventType.SPLITS))
                .build();

        var query = request.toQuery();

        assertThat(query).isEqualTo(HistoryQuery.of(Interval.ONE_WEEK)
                .range(Range.SIX_MONTHS)
                .includePrePost(true)
                .events(Set.of(EventType.SPLITS))
                .build());
    }

    @Test
    @SuppressWarnings({"deprecation", "removal"}) // HistoryRequest is the deprecated adapter under test here
    void historyRequestToQueryEquivalenceForPeriodForm() {
        var request = HistoryRequest.builder(Symbol.of("AAPL"))
                .interval(Interval.ONE_HOUR)
                .period(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000))
                .build();

        var query = request.toQuery();

        assertThat(query).isEqualTo(HistoryQuery.of(Interval.ONE_HOUR)
                .period(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(2000))
                .build());
    }

    @Test
    @SuppressWarnings({"deprecation", "removal"}) // HistoryRequest is the deprecated adapter under test here
    void historyRequestToQueryEquivalenceForOpenEndedPeriod() {
        var request = HistoryRequest.builder(Symbol.of("AAPL"))
                .period(Instant.ofEpochSecond(1000), null)
                .build();

        var query = request.toQuery();

        assertThat(query.hasPeriod()).isTrue();
        assertThat(query.end()).isEmpty();
        assertThat(query).isEqualTo(HistoryQuery.of(Interval.ONE_DAY).period(Instant.ofEpochSecond(1000)).build());
    }
}
