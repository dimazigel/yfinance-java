package io.github.dimazigel.yfinance.instrument;

import java.util.Currency;
import java.util.Objects;
import java.util.Optional;

/**
 * The currency Yahoo quotes an instrument in. Usually an ISO code ({@code USD}), but London quotes
 * in pence ({@code GBp}) and South Africa in cents ({@code ZAc}), which are not ISO currencies: the
 * raw code is always kept, and {@link #iso()} is present only when it is one.
 */
public record QuoteCurrency(String code, Optional<Currency> iso) {

    public QuoteCurrency {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(iso, "iso");
    }

    public static QuoteCurrency of(String code) {
        try {
            return new QuoteCurrency(code, Optional.of(Currency.getInstance(code)));
        } catch (IllegalArgumentException notIso) {
            return new QuoteCurrency(code, Optional.empty());
        }
    }

    /** Whether prices are in a minor unit (pence, cents): {@code GBp}, {@code ZAc}, {@code ILA}. */
    public boolean isPence() {
        return code.equals("GBp") || code.equals("ZAc") || code.equals("ILA");
    }
}
