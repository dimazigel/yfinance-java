package io.github.dimazigel.yfinance.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFMissingDataException;
import io.github.dimazigel.yfinance.exception.YFSkippedException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BatchTest {

    private static final Symbol AAPL = Symbol.of("AAPL");
    private static final Symbol MSFT = Symbol.of("MSFT");
    private static final Symbol NOPE = Symbol.of("NOPE");

    @Test
    void outcomesPreserveOrderAndSplitByKind() {
        var batch = new Batch<>(List.of(
                Outcome.ok(AAPL, 1), Outcome.skipped(MSFT, SkipReason.WRONG_ASSET_CLASS, "ETF"),
                Outcome.failed(NOPE, new YFDataException("boom"))));

        assertThat(batch.size()).isEqualTo(3);
        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(AAPL, MSFT, NOPE);
        assertThat(batch.values()).containsExactly(1);
        assertThat(batch.skipped()).singleElement().satisfies(s -> {
            assertThat(s.reason()).isEqualTo(SkipReason.WRONG_ASSET_CLASS);
            assertThat(s.detail()).isEqualTo("ETF");
        });
        assertThat(batch.failed()).singleElement().satisfies(f -> assertThat(f.error()).hasMessage("boom"));
        assertThat(batch.get(MSFT)).containsInstanceOf(Outcome.Skipped.class);
        assertThat(batch.get(Symbol.of("X"))).isEmpty();
        assertThat(batch.summary()).isEqualTo("3 symbols: 1 ok, 1 skipped, 1 failed");
    }

    @Test
    void outcomeAccessors() {
        Outcome<Integer> ok = Outcome.ok(AAPL, 7);
        assertThat(ok.optional()).contains(7);
        assertThat(ok.orElseThrow()).isEqualTo(7);

        Outcome<Integer> skipped = Outcome.skipped(MSFT, SkipReason.DOWNGRADED, "marketCap");
        assertThat(skipped.optional()).isEmpty();
        assertThatThrownBy(skipped::orElseThrow).isInstanceOf(YFDataException.class)
                .hasMessage("MSFT skipped: DOWNGRADED (marketCap)");

        var boom = new YFDataException("boom");
        Outcome<Integer> failed = Outcome.failed(NOPE, boom);
        assertThatThrownBy(failed::orElseThrow).isSameAs(boom);
    }

    @Test
    void skippedOrElseThrowNamesTheMissingFieldAndTheSymbol() {
        Outcome<Integer> skipped = Outcome.skipped(MSFT, SkipReason.MODULE_ABSENT, "financials.totalRevenue");

        assertThatThrownBy(skipped::orElseThrow)
                .isInstanceOf(YFMissingDataException.class)
                .isInstanceOf(YFSkippedException.class)
                .hasMessage("MSFT skipped: MODULE_ABSENT (financials.totalRevenue)")
                .satisfies(e -> {
                    var missing = (YFSkippedException) e;
                    assertThat(missing.field()).isEqualTo("financials.totalRevenue");
                    assertThat(missing.subject()).isEqualTo("MSFT");
                    assertThat(missing.reason()).isEqualTo(SkipReason.MODULE_ABSENT);   // what Tickers.fetch unwraps
                    assertThat(missing.symbol()).isEqualTo(MSFT);
                });
    }

    @Test
    void batchIsImmutableAndExhaustivelySwitchable() {
        var batch = new Batch<>(new java.util.ArrayList<>(List.of(Outcome.ok(AAPL, "v"))));
        assertThatThrownBy(() -> batch.outcomes().add(Outcome.ok(MSFT, "w")))
                .isInstanceOf(UnsupportedOperationException.class);
        String label = switch (batch.outcomes().getFirst()) {   // no default: sealed
            case Outcome.Ok<String> o -> "ok:" + o.value();
            case Outcome.Skipped<String> s -> "skipped";
            case Outcome.Failed<String> f -> "failed";
        };
        assertThat(label).isEqualTo("ok:v");
    }

    // --- batch B, item 1: ergonomics ---

    private static Batch<Integer> mixed() {
        return new Batch<>(List.of(
                Outcome.ok(AAPL, 1),
                Outcome.skipped(MSFT, SkipReason.WRONG_ASSET_CLASS, "ETF"),
                Outcome.failed(NOPE, new YFDataException("boom")),
                Outcome.ok(AAPL, 2)));   // a duplicate symbol, second value
    }

    @Test
    void batchIsIterableInInputOrder() {
        var seen = new ArrayList<Symbol>();
        for (Outcome<Integer> outcome : mixed()) {
            seen.add(outcome.symbol());
        }
        assertThat(seen).containsExactly(AAPL, MSFT, NOPE, AAPL);
    }

    @Test
    void streamAndOkAndAllOk() {
        var batch = mixed();
        assertThat(batch.stream().map(Outcome::symbol)).containsExactly(AAPL, MSFT, NOPE, AAPL);
        assertThat(batch.ok()).extracting(Outcome.Ok::value).containsExactly(1, 2);
        assertThat(batch.allOk()).isFalse();
        assertThat(new Batch<>(List.of(Outcome.ok(AAPL, 1), Outcome.ok(MSFT, 2))).allOk()).isTrue();
        assertThat(new Batch<Integer>(List.of()).allOk()).as("vacuously true").isTrue();
    }

    @Test
    void toMapKeepsInputOrderFirstWinsAndOmitsNonOk() {
        Map<Symbol, Integer> map = mixed().toMap();

        assertThat(map).containsExactly(Map.entry(AAPL, 1));   // MSFT skipped, NOPE failed, second AAPL ignored
        assertThat(new Batch<>(List.of(Outcome.ok(MSFT, 9), Outcome.ok(AAPL, 8))).toMap().keySet())
                .containsExactly(MSFT, AAPL);
        assertThatThrownBy(() -> map.put(NOPE, 0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void orElseThrowAllReturnsEveryValueWhenAllOk() {
        assertThat(new Batch<>(List.of(Outcome.ok(AAPL, 1), Outcome.ok(MSFT, 2))).orElseThrowAll()).containsExactly(1, 2);
        assertThat(new Batch<Integer>(List.of()).orElseThrowAll()).isEmpty();
    }

    @Test
    void orElseThrowAllThrowsTheFirstNonOkOutcome() {
        var skippedFirst = new Batch<>(List.of(
                Outcome.ok(AAPL, 1),
                Outcome.skipped(MSFT, SkipReason.DOWNGRADED, "marketCap"),
                Outcome.failed(NOPE, new YFDataException("boom"))));
        assertThatThrownBy(skippedFirst::orElseThrowAll)
                .isInstanceOf(YFSkippedException.class)
                .hasMessage("MSFT skipped: DOWNGRADED (marketCap)");

        var boom = new YFDataException("boom");
        var failedFirst = new Batch<>(List.of(
                Outcome.failed(NOPE, boom),
                Outcome.skipped(MSFT, SkipReason.DOWNGRADED, "marketCap")));
        assertThatThrownBy(failedFirst::orElseThrowAll).isInstanceOf(YFinanceException.class).isSameAs(boom);
    }

    @Test
    void mapRetypesOkValuesAndPassesSkippedAndFailedThrough() {
        Batch<String> mapped = mixed().map(i -> "v" + i);

        assertThat(mapped.size()).isEqualTo(4);
        assertThat(mapped.values()).containsExactly("v1", "v2");
        assertThat(mapped.outcomes().get(1)).isInstanceOfSatisfying(Outcome.Skipped.class, s -> {
            assertThat(s.symbol()).isEqualTo(MSFT);
            assertThat(s.reason()).isEqualTo(SkipReason.WRONG_ASSET_CLASS);
            assertThat(s.detail()).isEqualTo("ETF");
        });
        assertThat(mapped.outcomes().get(2)).isInstanceOfSatisfying(Outcome.Failed.class,
                f -> assertThat(f.error()).hasMessage("boom"));
        assertThat(mapped.outcomes()).extracting(Outcome::symbol).containsExactly(AAPL, MSFT, NOPE, AAPL);
    }

    @Test
    void outcomeMapOnEachKind() {
        Outcome<Integer> ok = Outcome.ok(AAPL, 7);
        Outcome<Integer> skipped = Outcome.skipped(MSFT, SkipReason.DOWNGRADED, "marketCap");
        var boom = new YFDataException("boom");
        Outcome<Integer> failed = Outcome.failed(NOPE, boom);

        assertThat(ok.map(i -> i * 2)).isEqualTo(Outcome.ok(AAPL, 14));
        assertThat(skipped.<String>map(i -> "x")).isEqualTo(Outcome.<String>skipped(MSFT, SkipReason.DOWNGRADED, "marketCap"));
        assertThat(failed.<String>map(i -> "x")).isEqualTo(Outcome.<String>failed(NOPE, boom));
    }

    @Test
    void getReturnsTheFirstOccurrenceOfADuplicateSymbol() {
        assertThat(mixed().get(AAPL)).contains(Outcome.ok(AAPL, 1));
    }
}
