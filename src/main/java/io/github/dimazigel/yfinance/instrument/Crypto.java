package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A cryptocurrency (Yahoo {@code quoteType} CRYPTOCURRENCY). A crypto is traded intraday with a
 * session, but not quoted with bid/ask (no book). It carries supply metrics and branding info.
 *
 * @param toCurrency The currency this crypto is quoted in. Yahoo reports this as an FX ticker
 *     such as {@code USD=X}; the suffix is stripped so the ISO currency resolves.
 */
public record Crypto(
        Core core,
        Session session,
        BigDecimal marketCap,
        Supply supply,
        BigDecimal volume24Hr,
        BigDecimal volumeAllCurrencies,
        String fromCurrency,
        QuoteCurrency toCurrency,
        LocalDate startDate,
        String lastMarket,
        Branding branding,
        Instant fetchedAt) implements Instrument, IntradayTraded {

    @Override
    public AssetClass assetClass() {
        return AssetClass.CRYPTO;
    }

    /** Circulating, total, and maximum supply of the cryptocurrency. */
    public record Supply(BigDecimal circulating, BigDecimal total, BigDecimal max) {}

    /** Branding and external links for the cryptocurrency. */
    public record Branding(URI image, URI logo, URI coinMarketCap) {}
}
