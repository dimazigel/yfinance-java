package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.EPOCH_DATE;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Crypto snapshot: core, session, and cryptocurrency-specific fields. */
public final class CryptoSpecs {

    private CryptoSpecs() {}

    private static final List<FieldSpec> OWN = List.of(
            required("marketCap", RAW, "v7:marketCap", "qs:summaryDetail.marketCap"),
            required("supply.circulating", RAW, "v7:circulatingSupply", "qs:summaryDetail.circulatingSupply"),
            required("supply.total", RAW, "v7:totalSupply", "qs:summaryDetail.totalSupply"),
            required("supply.max", RAW, "v7:maxSupply", "qs:summaryDetail.maxSupply"),
            required("volume24Hr", RAW, "v7:volume24Hr", "qs:summaryDetail.volume24Hr"),
            required("volumeAllCurrencies", RAW, "v7:volumeAllCurrencies", "qs:summaryDetail.volumeAllCurrencies"),
            required("fromCurrency", RAW, "v7:fromCurrency", "qs:summaryDetail.fromCurrency"),
            required("toCurrency", RAW, "v7:toCurrency", "qs:summaryDetail.toCurrency"),
            required("startDate", EPOCH_DATE, "v7:startDate", "qs:summaryDetail.startDate"),
            required("lastMarket", RAW, "v7:lastMarket", "qs:summaryDetail.lastMarket"),
            required("branding.image", RAW, "v7:coinImageUrl"),
            required("branding.logo", RAW, "v7:logoUrl"),
            required("branding.coinMarketCap", RAW, "v7:coinMarketCapLink", "qs:summaryDetail.coinMarketCapLink"));

    public static final List<FieldSpec> SNAPSHOT = Specs.concat(CoreSpecs.CORE, SessionSpecs.SESSION, OWN);
}
