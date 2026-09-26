package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.Core;
import io.github.dimazigel.yfinance.instrument.MarketState;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.ZoneId;

/** {@link Resolved} → {@link Core}. Callers must have checked {@code missingRequired()} first. */
public final class CoreBuilder {

    private CoreBuilder() {}

    public static Core build(Resolved r) {
        return new Core(
                Symbol.of(r.string("symbol")),
                r.string("shortName"),
                r.optString("longName"),
                QuoteCurrency.of(r.string("currency")),
                r.string("exchange"),
                r.string("fullExchangeName"),
                ZoneId.of(r.string("exchangeTimezone")),
                MarketState.fromWire(r.string("marketState")),
                r.decimal("price"),
                r.decimal("change"),
                r.decimal("changePercent"),
                r.decimal("previousClose"),
                r.instant("priceTime"),
                r.decimal("fiftyTwoWeekLow"),
                r.decimal("fiftyTwoWeekHigh"),
                r.decimal("fiftyDayAverage"),
                r.decimal("twoHundredDayAverage"),
                r.longValue("averageVolume10Day"),
                r.longValue("averageVolume3Month"),
                r.instant("firstTradeDate"),
                r.intValue("priceHint"),
                r.bool("hasPrePostMarketData"));
    }
}
