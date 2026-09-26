package io.github.dimazigel.yfinance.market;

import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Instrument metadata accompanying a price-history response.
 *
 * <p>Every field except {@code dataGranularity} is required: the chart mapper throws rather than
 * return a partially-populated metadata (history has no downgrade tier).
 *
 * @param regularMarketTime     time of the last regular-session trade Yahoo knew about
 * @param priceHint             number of decimal places Yahoo displays for this instrument
 * @param dataGranularity       the bar interval Yahoo actually served (may differ from the one
 *                              requested, e.g. 30m bars are built from 15m); absent when Yahoo
 *                              serves an interval string this version's {@link Interval} does not
 *                              know — an unknown granularity must not fail the whole history
 * @param validRanges           the {@link Range}s Yahoo accepts for this instrument; unknown values
 *                              are dropped
 * @param currentTradingPeriod  today's pre/regular/post sessions
 * @param hasPrePostMarketData  whether extended-hours bars exist for this instrument
 */
public record HistoryMetadata(
        Symbol symbol,
        QuoteCurrency currency,
        String exchangeName,
        String fullExchangeName,
        String instrumentType,
        ZoneId timezone,
        Instant firstTradeDate,
        BigDecimal regularMarketPrice,
        BigDecimal previousClose,
        Instant regularMarketTime,
        int priceHint,
        Optional<Interval> dataGranularity,
        List<Range> validRanges,
        TradingPeriods currentTradingPeriod,
        boolean hasPrePostMarketData) {

    public HistoryMetadata {
        validRanges = List.copyOf(validRanges);
    }

    /** One trading session: {@code [start, end)}. */
    public record TradingPeriod(Instant start, Instant end) {}

    /** The pre-market, regular and post-market sessions of one trading day. */
    public record TradingPeriods(TradingPeriod pre, TradingPeriod regular, TradingPeriod post) {}
}
