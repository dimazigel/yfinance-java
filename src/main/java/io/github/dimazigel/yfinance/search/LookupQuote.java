package io.github.dimazigel.yfinance.search;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.util.Optional;

/** A single instrument returned by the lookup endpoint; only the symbol is guaranteed. */
public record LookupQuote(
        Symbol symbol,
        Optional<String> shortName,
        Optional<String> quoteType,
        Optional<String> exchange,
        Optional<BigDecimal> regularMarketPrice) {}
