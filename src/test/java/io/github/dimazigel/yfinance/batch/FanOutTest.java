package io.github.dimazigel.yfinance.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFRateLimitException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FanOutTest {

    private static final Symbol AAPL = Symbol.of("AAPL");
    private static final Symbol MSFT = Symbol.of("MSFT");

    @Test
    void keepsInputOrderIncludingDuplicates() {
        var batch = FanOut.run(List.of(MSFT, AAPL, MSFT), 2, s -> Outcome.ok(s, s.value().toLowerCase(java.util.Locale.ROOT)));

        assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(MSFT, AAPL, MSFT);
        assertThat(batch.values()).containsExactly("msft", "aapl", "msft");
    }

    @Test
    void anExceptionEscapingTheWorkerBecomesFailed() {
        var rateLimited = new YFRateLimitException("slow down", null);

        var batch = FanOut.<String>run(List.of(AAPL, MSFT), 4, s -> {
            if (s.equals(AAPL)) {
                throw rateLimited;
            }
            throw new IllegalStateException("bug");
        });

        assertThat(batch.failed()).hasSize(2);
        assertThat(batch.failed().get(0).error()).as("library exceptions pass through as themselves").isSameAs(rateLimited);
        assertThat(batch.failed().get(1).error()).isInstanceOf(YFDataException.class)
                .hasMessage("Failed to fetch MSFT").hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void boundsConcurrency() throws Exception {
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();
        var symbols = List.of(AAPL, MSFT, Symbol.of("GOOG"), Symbol.of("AMZN"), Symbol.of("META"));

        var batch = FanOut.run(symbols, 2, s -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            return Outcome.ok(s, 1);
        });

        assertThat(batch.values()).hasSize(5);
        assertThat(maxInFlight.get()).isLessThanOrEqualTo(2);
    }

    @Test
    void emptyInputAndInvalidConcurrency() {
        assertThat(FanOut.run(List.of(), 1, s -> Outcome.ok(s, 1)).size()).isZero();
        assertThatThrownBy(() -> FanOut.run(List.of(AAPL), 0, s -> Outcome.ok(s, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
