package io.github.dimazigel.yfinance.assembly.specs;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import java.util.List;

/** Field specs for all asset classes, and fallback modules for snapshot-only fetches. */
public final class SnapshotSpecs {

    private SnapshotSpecs() {}

    /** Modules to fetch when a snapshot-only fallback is needed (symbol missing required field). */
    public static final List<String> FALLBACK_MODULES = List.of("price", "summaryDetail", "quoteType");

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
}
