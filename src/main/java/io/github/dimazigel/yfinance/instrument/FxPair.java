package io.github.dimazigel.yfinance.instrument;

import java.time.Instant;
import java.util.Optional;

/**
 * A foreign exchange pair (Yahoo {@code quoteType} CURRENCY). An FX pair is traded intraday with a
 * session and quoted with bid/ask.
 */
public record FxPair(
        Core core,
        Session session,
        Optional<TopOfBook> book,
        Instant fetchedAt) implements Instrument, IntradayTraded, Quoted {

    @Override
    public AssetClass assetClass() {
        return AssetClass.FX;
    }
}
