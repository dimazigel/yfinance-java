/**
 * One service per concern, sitting between the facade ({@code YFinance}, {@code Ticker},
 * {@code Tickers}) and the HTTP layer.
 *
 * <p><strong>The services are internal to the library — not API; they may change without
 * notice.</strong> The one public type here is
 * {@link io.github.dimazigel.yfinance.service.HistoryRequest}, the request object that
 * {@code Ticker.history(HistoryRequest)} takes; it is part of the API and follows its
 * compatibility rules.
 */
@NullMarked
package io.github.dimazigel.yfinance.service;

import org.jspecify.annotations.NullMarked;
