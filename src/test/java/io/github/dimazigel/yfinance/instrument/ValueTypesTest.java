package io.github.dimazigel.yfinance.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValueTypesTest {

    @Test
    void assetClassMapsYahooQuoteTypes() {
        assertThat(AssetClass.fromQuoteType("EQUITY")).contains(AssetClass.EQUITY);
        assertThat(AssetClass.fromQuoteType("ETF")).contains(AssetClass.ETF);
        assertThat(AssetClass.fromQuoteType("MUTUALFUND")).contains(AssetClass.MUTUAL_FUND);
        assertThat(AssetClass.fromQuoteType("INDEX")).contains(AssetClass.INDEX);
        assertThat(AssetClass.fromQuoteType("CRYPTOCURRENCY")).contains(AssetClass.CRYPTO);
        assertThat(AssetClass.fromQuoteType("CURRENCY")).contains(AssetClass.FX);
        assertThat(AssetClass.fromQuoteType("FUTURE")).contains(AssetClass.FUTURE);
        assertThat(AssetClass.fromQuoteType("OPTION")).isEmpty();
        assertThat(AssetClass.fromQuoteType("NONE")).isEmpty();
    }

    @Test
    void marketStateIsLenient() {
        assertThat(MarketState.fromWire("REGULAR")).isEqualTo(MarketState.REGULAR);
        assertThat(MarketState.fromWire("PREPRE")).isEqualTo(MarketState.PREPRE);
        assertThat(MarketState.fromWire("something-new")).isEqualTo(MarketState.OTHER);
    }

    @Test
    void quoteCurrencyKeepsNonIsoCodes() {
        assertThat(QuoteCurrency.of("USD")).isEqualTo(new QuoteCurrency("USD", Optional.of(Currency.getInstance("USD"))));
        assertThat(QuoteCurrency.of("GBp").iso()).isEmpty();      // pence: not an ISO code, but not lost either
        assertThat(QuoteCurrency.of("GBp").code()).isEqualTo("GBp");
        assertThat(QuoteCurrency.of("GBp").isPence()).isTrue();
        assertThat(QuoteCurrency.of("USD").isPence()).isFalse();
    }
}
