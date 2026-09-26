package io.github.dimazigel.yfinance.instrument;

import java.time.Instant;
import java.util.Optional;

/**
 * A stock market index (Yahoo {@code quoteType} INDEX). An index is traded intraday with a
 * session and quoted with bid/ask.
 */
public record Index(
        Core core,
        Session session,
        Optional<TopOfBook> book,
        Instant fetchedAt) implements Instrument, IntradayTraded, Quoted {

    @Override
    public AssetClass assetClass() {
        return AssetClass.INDEX;
    }
}
