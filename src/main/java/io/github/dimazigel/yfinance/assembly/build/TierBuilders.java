package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.PostMarket;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.instrument.Session;
import io.github.dimazigel.yfinance.instrument.TopOfBook;
import io.github.dimazigel.yfinance.instrument.TrailingDividend;
import java.util.Optional;

/** Builders for the tiers several classes share. */
final class TierBuilders {

    private TierBuilders() {}

    static Session session(Resolved r) {
        return new Session(r.decimal("open"), r.decimal("dayLow"), r.decimal("dayHigh"), r.longValue("volume"));
    }

    static Optional<TopOfBook> book(Resolved r) {
        if (!r.clusterPresent("book")) {
            return Optional.empty();
        }
        return Optional.of(new TopOfBook(r.decimal("bid"), r.decimal("ask"), r.optLong("bidSize"), r.optLong("askSize")));
    }

    static Optional<PostMarket> postMarket(Resolved r) {
        if (!r.clusterPresent("postMarket")) {
            return Optional.empty();
        }
        return Optional.of(new PostMarket(r.decimal("postMarketPrice"), r.decimal("postMarketChange"),
                r.decimal("postMarketChangePercent"), r.instant("postMarketTime")));
    }

    static Optional<Etf.EquityLikeStats> equityLikeStats(Resolved r) {
        if (!r.clusterPresent("equityLikeStats")) {
            return Optional.empty();
        }
        return Optional.of(new Etf.EquityLikeStats(r.decimal("equityLikeStats.bookValue"), r.decimal("equityLikeStats.priceToBook"),
                r.longValue("equityLikeStats.sharesOutstanding"), QuoteCurrency.of(r.string("equityLikeStats.financialCurrency"))));
    }

    static Optional<TrailingDividend> trailingDividend(Resolved r) {
        if (!r.clusterPresent("trailingDividend")) {
            return Optional.empty();
        }
        return Optional.of(new TrailingDividend(r.decimal("trailingDividend.rate"), r.decimal("trailingDividend.yield")));
    }
}
