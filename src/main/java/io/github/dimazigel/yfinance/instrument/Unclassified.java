package io.github.dimazigel.yfinance.instrument;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The downgrade target: only the universal core is known. Either Yahoo's {@code quoteType} is one
 * the model does not type ({@code attempted} empty), or it named a class whose guarantee failed
 * ({@code attempted} = that class, {@code missing} = the fields no source supplied). A failed
 * <em>detail</em> request never produces one of these: it is {@code Skipped(MODULE_ABSENT)} and the
 * classified snapshot stays as it was.
 */
public record Unclassified(
        Core core,
        String reportedQuoteType,
        Optional<AssetClass> attempted,
        List<String> missing,
        Instant fetchedAt) implements Instrument {

    public Unclassified {
        missing = List.copyOf(missing);
    }

    @Override
    public AssetClass assetClass() {
        return AssetClass.UNCLASSIFIED;
    }
}
