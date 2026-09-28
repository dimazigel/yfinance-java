package io.github.dimazigel.yfinance.http;

import java.io.InterruptedIOException;
import java.time.Duration;
import okhttp3.Interceptor;

/**
 * Keeps the waits the interceptors impose (rate-limit pacing, 5xx backoff) inside the call's
 * {@code callTimeout}. OkHttp's call timeout only <em>cancels</em> the call when it fires; a thread
 * asleep in an interceptor is not woken, so a wait longer than what is left of the budget would run
 * its full length and then fail with "timeout" anyway, having sent nothing. Failing before the wait
 * gives the same outcome sooner and keeps the budget the caller configured honest.
 */
final class CallBudget {

    private CallBudget() {}

    /**
     * Throws when {@code wait} does not fit in what remains of the call's timeout. {@code
     * enteredNanos} is when the interceptor was entered and {@code nowNanos} the current time, both
     * on the same clock; a call without a timeout ({@code timeoutNanos() == 0}) always fits.
     */
    static void ensureFits(Interceptor.Chain chain, long enteredNanos, long nowNanos, Duration wait, String what)
            throws InterruptedIOException {
        long budgetNanos = chain.call().timeout().timeoutNanos();
        if (budgetNanos == 0L) {
            return;
        }
        long remainingNanos = budgetNanos - (nowNanos - enteredNanos);
        if (wait.toNanos() > remainingNanos) {
            throw new InterruptedIOException(what + " wait of " + wait.toMillis() + " ms exceeds the remaining call timeout");
        }
    }
}
