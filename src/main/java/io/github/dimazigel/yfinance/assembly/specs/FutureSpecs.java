package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.EPOCH_DATE;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Future snapshot: core, session, book, and contract-specific fields. */
public final class FutureSpecs {

    private FutureSpecs() {}

    private static final List<FieldSpec> OWN = List.of(
            required("contract.isSpecificContract", RAW, "v7:contractSymbol"),
            required("contract.expireDate", EPOCH_DATE, "v7:expireDate"),
            required("contract.openInterest", RAW, "v7:openInterest"),
            required("contract.underlyingSymbol", RAW, "v7:underlyingSymbol"),
            required("contract.underlyingExchangeSymbol", RAW, "v7:underlyingExchangeSymbol"),
            required("contract.headSymbol", RAW, "v7:headSymbolAsString"));

    public static final List<FieldSpec> SNAPSHOT = Specs.concat(CoreSpecs.CORE, SessionSpecs.SESSION, BookSpecs.BOOK, OWN);
}
