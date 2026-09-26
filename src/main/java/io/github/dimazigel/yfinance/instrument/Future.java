package io.github.dimazigel.yfinance.instrument;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A futures contract (Yahoo {@code quoteType} FUTURE). A future is traded intraday with a session
 * and quoted with bid/ask (book). It carries contract-specific details.
 */
public record Future(
        Core core,
        Session session,
        Optional<TopOfBook> book,
        Contract contract,
        Instant fetchedAt) implements Instrument, IntradayTraded, Quoted {

    @Override
    public AssetClass assetClass() {
        return AssetClass.FUTURE;
    }

    /** Contract details for a futures instrument. {@code underlyingSymbol} is the front-month contract (e.g. {@code ESZ26.CME}); {@code headSymbol} is the continuous root (e.g. {@code ES=F}). */
    public record Contract(
            boolean isSpecificContract,
            LocalDate expireDate,
            long openInterest,
            Symbol underlyingSymbol,
            String underlyingExchangeSymbol,
            Symbol headSymbol) {}
}
