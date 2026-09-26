package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.Crypto;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import java.net.URI;
import java.time.Instant;

/** {@link Resolved} → {@link Crypto}. Callers must have checked {@code missingRequired()} first. */
public final class CryptoBuilder {

    private CryptoBuilder() {}

    public static Crypto build(Resolved r, Instant fetchedAt) {
        return new Crypto(
                CoreBuilder.build(r),
                TierBuilders.session(r),
                r.decimal("marketCap"),
                new Crypto.Supply(
                        r.decimal("supply.circulating"),
                        r.decimal("supply.total"),
                        r.decimal("supply.max")),
                r.decimal("volume24Hr"),
                r.decimal("volumeAllCurrencies"),
                r.string("fromCurrency"),
                QuoteCurrency.of(stripFxSuffix(r.string("toCurrency"))),
                r.date("startDate"),
                r.string("lastMarket"),
                new Crypto.Branding(
                        URI.create(r.string("branding.image")),
                        URI.create(r.string("branding.logo")),
                        URI.create(r.string("branding.coinMarketCap"))),
                fetchedAt);
    }

    private static String stripFxSuffix(String code) {
        return code.endsWith("=X") ? code.substring(0, code.length() - 2) : code;
    }
}
