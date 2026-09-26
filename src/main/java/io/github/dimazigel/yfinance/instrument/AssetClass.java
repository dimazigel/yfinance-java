package io.github.dimazigel.yfinance.instrument;

import java.util.Optional;

/** The asset classes the model distinguishes; maps from Yahoo's {@code quoteType}. */
public enum AssetClass {
    EQUITY, ETF, MUTUAL_FUND, INDEX, CRYPTO, FX, FUTURE, UNCLASSIFIED;

    /** The class for a Yahoo {@code quoteType}, empty for anything the model does not type. */
    public static Optional<AssetClass> fromQuoteType(String quoteType) {
        return Optional.ofNullable(switch (quoteType) {
            case "EQUITY" -> EQUITY;
            case "ETF" -> ETF;
            case "MUTUALFUND" -> MUTUAL_FUND;
            case "INDEX" -> INDEX;
            case "CRYPTOCURRENCY" -> CRYPTO;
            case "CURRENCY" -> FX;
            case "FUTURE" -> FUTURE;
            default -> null;
        });
    }
}
