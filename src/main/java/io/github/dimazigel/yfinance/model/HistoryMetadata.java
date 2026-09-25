package io.github.dimazigel.yfinance.model;

import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Instrument metadata accompanying a price-history response.
 *
 * @param regularMarketTime     time of the last regular-session trade Yahoo knew about
 * @param priceHint             number of decimal places Yahoo displays for this instrument
 * @param dataGranularity       the bar interval Yahoo actually served (may differ from the one
 *                              requested, e.g. 30m bars are built from 15m); {@code null} if unknown
 * @param validRanges           the {@link Range}s Yahoo accepts for this instrument; unknown values
 *                              are dropped
 * @param currentTradingPeriod  today's pre/regular/post sessions, when Yahoo reports them
 * @param hasPrePostMarketData  whether extended-hours bars exist for this instrument
 */
public record HistoryMetadata(
        Symbol symbol,
        @Nullable Currency currency,
        @Nullable String exchangeName,
        @Nullable String fullExchangeName,
        @Nullable String instrumentType,
        @Nullable ZoneId timezone,
        @Nullable Instant firstTradeDate,
        @Nullable BigDecimal regularMarketPrice,
        @Nullable BigDecimal previousClose,
        @Nullable Instant regularMarketTime,
        @Nullable Integer priceHint,
        @Nullable Interval dataGranularity,
        List<Range> validRanges,
        @Nullable TradingPeriods currentTradingPeriod,
        @Nullable Boolean hasPrePostMarketData) {

    public HistoryMetadata {
        validRanges = validRanges == null ? List.of() : List.copyOf(validRanges);
    }

    /** One trading session: {@code [start, end)}. */
    public record TradingPeriod(Instant start, Instant end) {}

    /** The pre-market, regular and post-market sessions of one trading day. */
    public record TradingPeriods(TradingPeriod pre, TradingPeriod regular, TradingPeriod post) {}
}
