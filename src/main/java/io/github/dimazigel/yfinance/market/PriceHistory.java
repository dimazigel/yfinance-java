package io.github.dimazigel.yfinance.market;

import java.time.ZoneId;
import java.util.List;

/** Result of a price-history query: candles plus corporate actions and metadata. */
public record PriceHistory(
        HistoryMetadata metadata,
        List<PriceBar> bars,
        List<Dividend> dividends,
        List<Split> splits,
        List<CapitalGain> capitalGains) {

    public PriceHistory {
        bars = bars == null ? List.of() : List.copyOf(bars);
        dividends = dividends == null ? List.of() : List.copyOf(dividends);
        splits = splits == null ? List.of() : List.copyOf(splits);
        capitalGains = capitalGains == null ? List.of() : List.copyOf(capitalGains);
    }

    /** This history with every bar {@link PriceBar#adjusted() adjusted}; metadata and events unchanged. */
    public PriceHistory adjusted() {
        return new PriceHistory(metadata, bars.stream().map(PriceBar::adjusted).toList(), dividends, splits, capitalGains);
    }

    /**
     * The exchange timezone for this instrument. Pass this to {@link Dividend#localDate(ZoneId)} and
     * friends to get correct trading dates.
     */
    public ZoneId zoneId() {
        return metadata.timezone();
    }
}
