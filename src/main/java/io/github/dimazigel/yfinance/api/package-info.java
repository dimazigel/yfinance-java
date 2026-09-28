/**
 * Feign bindings, one interface per Yahoo endpoint, bundled by {@code YahooApis}.
 *
 * <p><strong>Internal to the library — not API; may change without notice.</strong> The one exception a
 * caller may touch is {@code YahooApis}, the argument of {@code YFinance.fromApis(...)} for tests
 * and advanced wiring; its shape follows Yahoo's endpoints, not a stable contract.
 */
@NullMarked
package io.github.dimazigel.yfinance.api;

import org.jspecify.annotations.NullMarked;
