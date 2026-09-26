package io.github.dimazigel.yfinance.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFMissingDataException;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequiredTest {

    private static final Quote INDEX_QUOTE = new Quote(
            Symbol.of("^GSPC"), "S&P 500", "S&P 500", "INDEX", "SNP", null, "REGULAR",
            new Quote.PriceSnapshot(new BigDecimal("7706.96"), null, null, null, null, null, null, null, null, null, null),
            new Quote.KeyStats(null, null, null, null, null, null, null, null),
            new Quote.AnalystSummary(null, null, null, null, null, null));

    @Test
    void genericHelperReturnsPresentValuesAndNamesMissingOnes() {
        // Assign first, as real callers do: a lambda argument leaves V open inside an overloaded assertThat.
        BigDecimal price = Required.value(INDEX_QUOTE, q -> q.price().regularMarketPrice(), "regularMarketPrice");
        assertThat(price).isEqualByComparingTo("7706.96");

        assertThatThrownBy(() -> Required.value(INDEX_QUOTE, q -> q.price().marketCap(), "marketCap"))
                .isInstanceOf(YFMissingDataException.class)
                .isInstanceOf(YFDataException.class) // still inside the library's exception family
                .hasMessage("marketCap is not available (Quote)")
                .satisfies(e -> {
                    var missing = (YFMissingDataException) e;
                    assertThat(missing.field()).isEqualTo("marketCap");
                    assertThat(missing.subject()).isEqualTo("Quote");
                });
    }

    @Test
    void quoteAndInfoNameTheSymbol() {
        assertThat(INDEX_QUOTE.require(Quote::longName, "longName")).isEqualTo("S&P 500");

        assertThatThrownBy(() -> INDEX_QUOTE.require(q -> q.keyStats().trailingPe(), "trailingPe"))
                .isInstanceOf(YFMissingDataException.class)
                .hasMessage("trailingPe is not available for ^GSPC");

        var info = new Info(null, INDEX_QUOTE, List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> info.require(Info::profile, "profile"))
                .isInstanceOf(YFMissingDataException.class)
                .hasMessage("profile is not available for ^GSPC");
        BigDecimal viaInfo = info.require(i -> i.quote().price().regularMarketPrice(), "regularMarketPrice");
        assertThat(viaInfo).isPositive();
    }
}
