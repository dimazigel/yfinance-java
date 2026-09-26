/**
 * The typed instrument model: a sealed {@link io.github.dimazigel.yfinance.instrument.Instrument} hierarchy whose
 * records carry no {@code @Nullable} component. Null-safety of constructor arguments is enforced by NullAway at
 * compile time (the library builds every instance); the records do not repeat the checks at run time.
 */
@NullMarked
package io.github.dimazigel.yfinance.instrument;

import org.jspecify.annotations.NullMarked;
