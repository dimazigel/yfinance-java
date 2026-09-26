package io.github.dimazigel.yfinance.assembly.specs;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import io.github.dimazigel.yfinance.assembly.Source;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import java.util.LinkedHashSet;
import java.util.List;

/** Field specs for all asset classes, and fallback modules for snapshot-only fetches. */
public final class SnapshotSpecs {

    private SnapshotSpecs() {}

    /**
     * The modules every class's fallback starts with — enough for the universal core and most
     * near-universal fields. {@link #fallbackModules(AssetClass)}, not this list directly, is what
     * {@code InstrumentService} actually requests: it extends this base with whatever other
     * quoteSummary modules that class's own required fields reach into (e.g. {@code fundPerformance}
     * for ETFs and mutual funds), so a class whose guarantee lives outside these three modules still
     * gets a fallback that can satisfy it.
     */
    public static final List<String> BASE_FALLBACK_MODULES = List.of("price", "summaryDetail", "quoteType");

    /** Field specs for snapshot construction by asset class. */
    public static List<FieldSpec> forClass(AssetClass assetClass) {
        return switch (assetClass) {
            case EQUITY -> EquitySpecs.SNAPSHOT;
            case ETF -> EtfSpecs.SNAPSHOT;
            case MUTUAL_FUND -> MutualFundSpecs.SNAPSHOT;
            case INDEX, FX -> SimpleSpecs.SIMPLE;
            case CRYPTO -> CryptoSpecs.SNAPSHOT;
            case FUTURE -> FutureSpecs.SNAPSHOT;
            case UNCLASSIFIED -> CoreSpecs.CORE;
        };
    }

    /**
     * Modules to request for {@code assetClass}'s single-symbol snapshot fallback:
     * {@link #BASE_FALLBACK_MODULES} first, then every other distinct quoteSummary module that
     * {@link #forClass(AssetClass)}'s own fields reference, in the order the specs declare them.
     * Derived from the specs themselves (each {@link FieldSpec}'s {@code qs:} paths, keyed by the
     * module — the path's first {@code .}-segment) rather than hand-maintained, so a class whose
     * guarantee moves to a new module picks up the fallback for it automatically instead of quietly
     * downgrading every symbol that needed that module.
     */
    public static List<String> fallbackModules(AssetClass assetClass) {
        var modules = new LinkedHashSet<>(BASE_FALLBACK_MODULES);
        for (FieldSpec spec : forClass(assetClass)) {
            for (var path : spec.paths()) {
                if (path.source() == Source.QUOTE_SUMMARY) {
                    modules.add(path.path().split("\\.", 2)[0]);
                }
            }
        }
        return List.copyOf(modules);
    }
}
