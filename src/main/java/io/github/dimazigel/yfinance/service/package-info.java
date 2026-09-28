/**
 * One service per concern, sitting between the facade ({@code YFinance}, {@code Ticker},
 * {@code Tickers}) and the HTTP layer.
 *
 * <p><strong>This package is internal to the library — not API; it may change without
 * notice.</strong> The one exception is
 * {@link io.github.dimazigel.yfinance.service.HistoryRequest}, kept only as a deprecated adapter
 * for {@code Ticker.history(HistoryRequest)} until its removal in 2.0. The current price-history
 * query type is {@link io.github.dimazigel.yfinance.market.HistoryQuery}.
 */
@NullMarked
package io.github.dimazigel.yfinance.service;

import org.jspecify.annotations.NullMarked;
