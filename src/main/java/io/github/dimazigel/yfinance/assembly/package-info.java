/**
 * Generic wire-path resolver: field tables ({@link io.github.dimazigel.yfinance.assembly.FieldSpec})
 * resolved against raw v7/quoteSummary JSON ({@link io.github.dimazigel.yfinance.assembly.Payload})
 * into typed, unit-aware values ({@link io.github.dimazigel.yfinance.assembly.Resolved}).
 *
 * <p><strong>Internal to the library — not API; may change without notice.</strong> The types are
 * {@code public} only because the services and builders live in other packages; they expose
 * Jackson's {@code JsonNode}, which the public model never does.
 */
@NullMarked
package io.github.dimazigel.yfinance.assembly;

import org.jspecify.annotations.NullMarked;
