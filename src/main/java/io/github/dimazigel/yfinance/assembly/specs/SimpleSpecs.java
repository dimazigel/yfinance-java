package io.github.dimazigel.yfinance.assembly.specs;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Index and FxPair snapshot: core, session, and book. */
public final class SimpleSpecs {

    private SimpleSpecs() {}

    public static final List<FieldSpec> SIMPLE = Specs.concat(CoreSpecs.CORE, SessionSpecs.SESSION, BookSpecs.BOOK);
}
