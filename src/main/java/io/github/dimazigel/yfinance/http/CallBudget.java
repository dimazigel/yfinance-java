package io.github.dimazigel.yfinance.http;

import java.io.InterruptedIOException;
import java.time.Duration;
import okhttp3.Interceptor;

/**
 * Keeps the waits the interceptors impose (rate-limit pacing, 5xx backoff) inside the call's
 * {@code callTimeout}. OkHttp's call timeout only <em>cancels</em> the call when it fires; a thread
 * asleep in an interceptor is not woken, so a wait longer than what is left of the budget would run
 * its full length and then fail with "timeout" anyway, having sent nothing. Failing before the wait
 * gives the same outcome sooner and keeps the budget the caller configured honest. Waits are also
 * sliced (at most {@link #SLICE}) with a cancellation check between slices, so a cancelled call
 * stops promptly.
 */
final class CallBudget {

    /** Longest single sleep an interceptor takes before re-checking cancellation. */
    static final Duration SLICE = Duration.ofSeconds(1);

    private CallBudget() {}

    /**
     * Whether {@code wait} fits in what remains of the call's timeout. {@code enteredNanos} is when
     * the interceptor was entered and {@code nowNanos} the current time, both on the same clock; a
     * call without a timeout ({@code timeoutNanos() == 0}) always fits.
     */
    static boolean fits(Interceptor.Chain chain, long enteredNanos, long nowNanos, Duration wait) {
        long budgetNanos = chain.call().timeout().timeoutNanos();
        if (budgetNanos == 0L) {
            return true;
        }
        return wait.toNanos() <= budgetNanos - (nowNanos - enteredNanos);
    }

    /** Throws {@code InterruptedIOException("Canceled")} once the call has been cancelled (by the caller or the call timeout). */
    static void checkNotCanceled(Interceptor.Chain chain) throws InterruptedIOException {
        if (chain.call().isCanceled()) {
            throw new InterruptedIOException("Canceled");
        }
    }

    /** {@code wait} capped to one {@link #SLICE}. */
    static Duration slice(Duration wait) {
        return wait.compareTo(SLICE) > 0 ? SLICE : wait;
    }
}
