package io.github.dimazigel.yfinance.market;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.enums.Interval;
import io.github.dimazigel.yfinance.enums.Range;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TradingDateTest {

    // 2024-01-01T04:00:00Z — still 2023-12-31 in New York (UTC-5 in January).
    private static final Instant EVENT = Instant.ofEpochSecond(1704081600L);
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    void dividendLocalDateUsesExchangeZoneNotUtc() {
        var dividend = new Dividend(EVENT, new BigDecimal("0.24"));
        assertThat(dividend.localDate(ZoneOffset.UTC)).isEqualTo(LocalDate.parse("2024-01-01"));
        assertThat(dividend.localDate(NEW_YORK)).isEqualTo(LocalDate.parse("2023-12-31"));
    }

    @Test
    void splitAndCapitalGainExposeLocalDate() {
        var split = new Split(EVENT, BigDecimal.valueOf(4), BigDecimal.ONE, "4:1");
        var gain = new CapitalGain(EVENT, new BigDecimal("1.0"));
        assertThat(split.localDate(NEW_YORK)).isEqualTo(LocalDate.parse("2023-12-31"));
        assertThat(gain.localDate(NEW_YORK)).isEqualTo(LocalDate.parse("2023-12-31"));
    }

    @Test
    void priceHistoryZoneIdComesFromMetadata() {
        // Every history has a timezone now (ChartMapper throws rather than serve an incomplete one),
        // so PriceHistory.zoneId() reads straight through to it — no more UTC fallback to test.
        var meta = fullMetadata(NEW_YORK);
        var history = new PriceHistory(meta, List.of(), List.of(), List.of(), List.of());
        assertThat(history.zoneId()).isEqualTo(NEW_YORK);
    }

    private static HistoryMetadata fullMetadata(ZoneId zone) {
        var session = new HistoryMetadata.TradingPeriod(EVENT, EVENT.plusSeconds(3600));
        return new HistoryMetadata(Symbol.of("AAPL"), QuoteCurrency.of("USD"), "NMS", "NasdaqGS", "EQUITY",
                zone, EVENT, new BigDecimal("100"), new BigDecimal("99"), EVENT, 2, Optional.of(Interval.ONE_DAY),
                List.of(Range.ONE_DAY), new HistoryMetadata.TradingPeriods(session, session, session), true);
    }
}
