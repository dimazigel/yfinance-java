package io.ziggy.yfinance.mapper;

import io.ziggy.yfinance.dto.quote.QuoteResponse;
import io.ziggy.yfinance.dto.quote.QuoteResponse.Result;
import io.ziggy.yfinance.exception.YFDataException;
import io.ziggy.yfinance.model.Quote;
import io.ziggy.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Maps the raw {@code /v7/finance/quote} response into {@link Quote}s. The same model as
 * quoteSummary-derived quotes, so callers can treat both sources alike; fields this endpoint does
 * not serve (beta, price targets, revenue, margins) are {@code null}.
 */
public final class QuoteMapper {

    private QuoteMapper() {}

    /**
     * Quotes keyed by symbol in {@code requested} order; symbols Yahoo did not return are absent.
     *
     * @throws YFDataException on a malformed response or a Yahoo error envelope
     */
    public static Map<Symbol, Quote> toQuotes(QuoteResponse response, List<Symbol> requested) {
        var envelope = response.quoteResponse();
        if (envelope == null) {
            throw new YFDataException("Malformed quote response for " + requested);
        }
        if (envelope.error() != null) {
            throw new YFDataException("Yahoo error for " + requested + ": " + MapperSupport.describe(envelope.error()));
        }
        var bySymbol = new LinkedHashMap<Symbol, Quote>();
        for (Result result : envelope.result() == null ? List.<Result>of() : envelope.result()) {
            String symbol = result.symbol();
            if (symbol != null && !symbol.isBlank()) {
                bySymbol.put(Symbol.of(symbol), toQuote(result, Symbol.of(symbol)));
            }
        }
        var ordered = new LinkedHashMap<Symbol, Quote>();
        for (Symbol symbol : requested) {
            Quote quote = bySymbol.get(symbol);
            if (quote != null) {
                ordered.put(symbol, quote);
            }
        }
        return ordered;
    }

    static Quote toQuote(Result r, Symbol symbol) {
        Rating rating = Rating.parse(r.averageAnalystRating());
        return new Quote(
                symbol,
                r.longName(),
                r.shortName(),
                r.quoteType(),
                r.exchange(),
                MapperSupport.currency(r.currency()),
                r.marketState(),
                new Quote.PriceSnapshot(
                        r.regularMarketPrice(),
                        r.regularMarketChange(),
                        r.regularMarketChangePercent(),
                        r.regularMarketPreviousClose(),
                        r.regularMarketOpen(),
                        r.regularMarketDayLow(),
                        r.regularMarketDayHigh(),
                        r.regularMarketVolume(),
                        r.fiftyTwoWeekLow(),
                        r.fiftyTwoWeekHigh(),
                        r.marketCap()),
                new Quote.KeyStats(
                        r.trailingPE(),
                        r.epsTrailingTwelveMonths(),
                        r.epsForward(),
                        r.bookValue(),
                        r.priceToBook(),
                        null, // beta is not served by /v7/finance/quote
                        r.sharesOutstanding(),
                        r.trailingAnnualDividendYield()),
                new Quote.AnalystSummary(
                        null, rating.mean(), rating.key(), null, null, null));
    }

    /**
     * Yahoo's {@code averageAnalystRating} is a display string such as {@code "2.2 - Buy"} or
     * {@code "1.6 - Strong Buy"}; split it into the numeric mean and quoteSummary's
     * {@code recommendationKey} vocabulary ({@code buy}, {@code strong_buy}, ...).
     */
    record Rating(@Nullable BigDecimal mean, @Nullable String key) {

        static final Rating NONE = new Rating(null, null);

        static Rating parse(@Nullable String display) {
            if (display == null || display.isBlank()) {
                return NONE;
            }
            String[] parts = display.split(" - ", 2);
            BigDecimal mean = null;
            String text = parts[parts.length - 1];
            if (parts.length == 2) {
                try {
                    mean = new BigDecimal(parts[0].strip());
                } catch (NumberFormatException e) {
                    text = display;
                }
            }
            String key = text.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
            return new Rating(mean, key.isEmpty() ? null : key);
        }
    }
}
