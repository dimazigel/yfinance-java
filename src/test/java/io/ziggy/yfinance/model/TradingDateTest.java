package io.ziggy.yfinance.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.ziggy.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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
    void priceHistoryZoneIdComesFromMetadataAndFallsBackToUtc() {
        var withZone = new HistoryMetadata(
                Symbol.of("AAPL"), null, null, null, null, NEW_YORK, null, null, null, null, null, null, List.of(), null, null);
        var historyNy = new PriceHistory(withZone, List.of(), List.of(), List.of(), List.of());
        assertThat(historyNy.zoneId()).isEqualTo(NEW_YORK);

        var noZone = new HistoryMetadata(
                Symbol.of("AAPL"), null, null, null, null, null, null, null, null, null, null, null, List.of(), null, null);
        var historyUtc = new PriceHistory(noZone, List.of(), List.of(), List.of(), List.of());
        assertThat(historyUtc.zoneId()).isEqualTo(ZoneOffset.UTC);
    }
}
