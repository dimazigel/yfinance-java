package io.github.dimazigel.yfinance.instrument;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The downgrade target: only the universal core is known. Either Yahoo's {@code quoteType} is one
 * the model does not type ({@code attempted} empty), or it named a class whose guarantee failed
 * ({@code attempted} = that class, {@code missing} = the fields no source supplied).
 *
 * @param snapshot the classified snapshot when only a <em>detail</em> request failed (design §4.2)
 */
public record Unclassified(
        Core core,
        String reportedQuoteType,
        Optional<AssetClass> attempted,
        List<String> missing,
        Optional<Instrument> snapshot,
        Instant fetchedAt) implements Instrument {

    public Unclassified {
        missing = List.copyOf(missing);
    }

    @Override
    public AssetClass assetClass() {
        return AssetClass.UNCLASSIFIED;
    }
}
