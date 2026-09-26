package io.github.dimazigel.yfinance.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFMissingDataException;
import io.github.dimazigel.yfinance.exception.YFSkippedException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
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
}
