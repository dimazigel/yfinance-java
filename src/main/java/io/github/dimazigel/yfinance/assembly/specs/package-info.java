/**
 * Field specifications for assembling model types from raw JSON. One table (like {@link
 * io.github.dimazigel.yfinance.assembly.specs.CoreSpecs#CORE}) per instrument concern, describing
 * the fields present, their wire paths, and unit transformations; Appendix A of the design is the
 * normative copy and {@code AppendixConformanceTest} keeps the two in step.
 *
 * <p><strong>Internal to the library — not API; may change without notice.</strong>
 */
@NullMarked
package io.github.dimazigel.yfinance.assembly.specs;

import org.jspecify.annotations.NullMarked;
