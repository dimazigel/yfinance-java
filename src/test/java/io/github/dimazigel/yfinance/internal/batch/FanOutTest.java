package io.github.dimazigel.yfinance.internal.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFRateLimitException;
import io.github.dimazigel.yfinance.testsupport.LogCapture;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
    void workersInheritTheCallersMdc() {   // robustness review, item 6
        // A service's traceId/requestId must reach the lines logged on the worker threads.
        MDC.put("traceId", "t-1");
        var log = LoggerFactory.getLogger(FanOutTest.class);
        try (var capture = LogCapture.of(FanOutTest.class)) {
            var batch = FanOut.run(List.of(AAPL, MSFT), 2, s -> {
                log.atDebug().log("working on {}", s);
                return Outcome.ok(s, String.valueOf(MDC.get("traceId")));
            });

            assertThat(batch.values()).containsExactly("t-1", "t-1");
            assertThat(capture.events()).hasSize(2)
                    .allSatisfy(e -> assertThat(e.getMDCPropertyMap()).containsEntry("traceId", "t-1"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void anErrorOnAWorkerPropagatesInsteadOfBecomingAFailedOutcome() {   // robustness review, item 11
        var oom = new OutOfMemoryError("simulated");

        assertThatThrownBy(() -> FanOut.<String>run(List.of(AAPL, MSFT), 2, s -> {
            if (s.equals(AAPL)) {
                throw oom;
            }
            return Outcome.ok(s, "fine");
        })).isSameAs(oom);
    }

    /**
     * The caller is interrupted while it waits for a slow symbol: the batch must come back at once
     * rather than wait the slow workers out, keep what already finished, report every unfinished
     * symbol as failed, stop the workers, and leave the interrupt flag set for the caller's caller.
     */
    @Test
    void interruptingTheCallerFailsUnfinishedSymbolsStopsTheirWorkersAndKeepsTheFlag() throws Exception {
        Symbol goog = Symbol.of("GOOG");
        var slowWorkersStarted = new CountDownLatch(2);
        var quickWorkerReturned = new CountDownLatch(1);
        var never = new CountDownLatch(1);
        var workersInterrupted = new AtomicInteger();
        var flagAfterRun = new AtomicBoolean();
        var result = new AtomicReference<Batch<String>>();

        // MSFT first, so the caller is parked on a symbol that will not finish by itself.
        Thread caller = Thread.ofPlatform().start(() -> {
            result.set(FanOut.run(List.of(MSFT, AAPL, goog), 3, s -> {
                if (s.equals(AAPL)) {
                    quickWorkerReturned.countDown();
                    return Outcome.ok(s, "done");
                }
                slowWorkersStarted.countDown();
                try {
                    never.await();
                } catch (InterruptedException e) {
                    workersInterrupted.incrementAndGet();
                    throw new IllegalStateException("cancelled", e);
                }
                return Outcome.ok(s, "unreachable");
            }));
            flagAfterRun.set(Thread.currentThread().isInterrupted());
        });
        try {
            assertThat(slowWorkersStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(quickWorkerReturned.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);   // let the quick worker's future complete after its function returned

            caller.interrupt();
            caller.join(5_000);

            assertThat(caller.isAlive()).as("run returns instead of waiting for the slow workers").isFalse();
            Batch<String> batch = result.get();
            assertThat(batch.outcomes()).extracting(Outcome::symbol).containsExactly(MSFT, AAPL, goog);
            assertThat(batch.get(AAPL).orElseThrow().optional()).as("a finished symbol keeps its outcome").contains("done");
            assertThat(batch.failed()).extracting(Outcome.Failed::symbol).containsExactly(MSFT, goog);
            assertThat(batch.failed()).allSatisfy(f -> {
                assertThat(f.error()).isInstanceOf(YFDataException.class)
                        .hasMessage("Interrupted fetching " + f.symbol())
                        .hasCauseInstanceOf(InterruptedException.class);
                assertThat(f.isRetryable()).isFalse();
            });
            assertThat(workersInterrupted).as("both slow workers were interrupted, not left running").hasValue(2);
            assertThat(flagAfterRun).as("the interrupt is still pending for the caller").isTrue();
        } finally {
            never.countDown();
        }
    }

    @Test
    void emptyInputAndInvalidConcurrency() {
        assertThat(FanOut.run(List.of(), 1, s -> Outcome.ok(s, 1)).size()).isZero();
        assertThatThrownBy(() -> FanOut.run(List.of(AAPL), 0, s -> Outcome.ok(s, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
