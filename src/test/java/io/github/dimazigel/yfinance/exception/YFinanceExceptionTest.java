package io.github.dimazigel.yfinance.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** {@code isRetryable()} is the machine-readable form of the README's error table. */
class YFinanceExceptionTest {

    @Test
    void baseAndAuthAreNotRetryable() {
        assertThat(new YFinanceException("x").isRetryable()).isFalse();
        assertThat(new YFinanceException("x", new IOException("io")).isRetryable()).as("the base type has no rule").isFalse();
        assertThat(new YFAuthException("consent page").isRetryable()).isFalse();
        assertThat(new YFAuthException("handshake", new IOException("io")).isRetryable()).isFalse();
    }

    @Test
    void rateLimitIsRetryable() {
        assertThat(new YFRateLimitException("429").isRetryable()).isTrue();
        assertThat(new YFRateLimitException("429", Duration.ofSeconds(3)).isRetryable()).isTrue();
    }

    @Test
    void httpIsRetryableForServerErrorsOnly() {
        assertThat(new YFHttpException(500, "/p", "boom").isRetryable()).isTrue();
        assertThat(new YFHttpException(503, "/p", "boom").isRetryable()).isTrue();
        assertThat(new YFHttpException(404, "/p", "not found").isRetryable()).isFalse();
        assertThat(new YFHttpException(400, "/p", "bad").isRetryable()).isFalse();
    }

    @Test
    void dataIsRetryableWhenCausedByIo() {
        assertThat(new YFDataException("I/O error calling Yahoo Finance", new SocketTimeoutException("read")).isRetryable()).isTrue();
        assertThat(new YFDataException("I/O error", new IOException("reset")).isRetryable()).isTrue();
        assertThat(new YFDataException("Malformed JSON").isRetryable()).isFalse();
        assertThat(new YFDataException("wrapped", new IllegalStateException("bug")).isRetryable()).isFalse();
    }

    @Test
    void missingSkippedAndClassMismatchAreNeverRetryable() {
        assertThat(new YFMissingDataException("marketCap", "AAPL", "missing").isRetryable()).isFalse();
        assertThat(new YFSkippedException(Symbol.of("AAPL"), SkipReason.UNKNOWN_SYMBOL, "absent").isRetryable()).isFalse();
        assertThat(new YFClassMismatchException(Symbol.of("AAPL"), AssetClass.EQUITY, Equity.class).isRetryable()).isFalse();
    }
}
