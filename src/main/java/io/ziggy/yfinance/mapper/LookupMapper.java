package io.ziggy.yfinance.mapper;

import io.ziggy.yfinance.dto.lookup.LookupResponse;
import io.ziggy.yfinance.exception.YFDataException;
import io.ziggy.yfinance.model.LookupQuote;
import io.ziggy.yfinance.valueobject.Symbol;
import java.util.List;
import java.util.Objects;

/** Maps the raw lookup response into a list of {@link LookupQuote}. */
public final class LookupMapper {

    private LookupMapper() {}

    public static List<LookupQuote> toQuotes(LookupResponse response) {
        var finance = response.finance();
        if (finance == null) {
            throw new YFDataException("Malformed lookup response");
        }
        if (finance.error() != null) {
            throw new YFDataException("Yahoo lookup error: " + finance.error().description());
        }
        if (finance.result() == null) {
            return List.of();
        }
        return finance.result().stream()
                .filter(r -> r.documents() != null)
                .flatMap(r -> r.documents().stream())
                .filter(d -> d.symbol() != null && !d.symbol().isBlank())
                .map(d -> new LookupQuote(
                        Symbol.of(Objects.requireNonNull(d.symbol())), // filtered above
                        d.shortName(), d.quoteType(), d.exchange(), d.regularMarketPrice()))
                .toList();
    }
}
