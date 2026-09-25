package io.ziggy.yfinance.mapper;

import static io.ziggy.yfinance.mapper.MapperSupport.from;

import io.ziggy.yfinance.dto.quotesummary.QuoteSummaryResponse.AssetProfile;
import io.ziggy.yfinance.dto.quotesummary.QuoteSummaryResponse.DefaultKeyStatistics;
import io.ziggy.yfinance.dto.quotesummary.QuoteSummaryResponse.FinancialData;
import io.ziggy.yfinance.dto.quotesummary.QuoteSummaryResponse.Price;
import io.ziggy.yfinance.dto.quotesummary.QuoteSummaryResponse.QuoteType;
import io.ziggy.yfinance.dto.quotesummary.QuoteSummaryResponse.Result;
import io.ziggy.yfinance.dto.quotesummary.QuoteSummaryResponse.SummaryDetail;
import io.ziggy.yfinance.dto.quotesummary.QuoteSummaryResponse;
import io.ziggy.yfinance.exception.YFDataException;
import io.ziggy.yfinance.model.CompanyProfile.CompanyOfficer;
import io.ziggy.yfinance.model.CompanyProfile;
import io.ziggy.yfinance.model.Info;
import io.ziggy.yfinance.model.Quote;
import io.ziggy.yfinance.model.RecommendationPeriod;
import io.ziggy.yfinance.model.SecFiling;
import io.ziggy.yfinance.model.UpgradeDowngrade;
import io.ziggy.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps the raw quoteSummary response into the consolidated {@link Info} model. */
public final class QuoteSummaryMapper {

    private QuoteSummaryMapper() {}

    public static Info toInfo(QuoteSummaryResponse response, Symbol requested) {
        Result result = requireResult(response, requested);
        return new Info(
                mapProfile(result.assetProfile()),
                mapQuote(result, requested),
                mapRecommendations(result),
                mapUpgrades(result),
                mapEarningsDates(result),
                mapSecFilings(result));
    }

    /** Extracts the single result object, raising {@link YFDataException} on Yahoo errors. */
    public static Result requireResult(QuoteSummaryResponse response, Symbol requested) {
        var summary = response.quoteSummary();
        if (summary == null) {
            throw new YFDataException("Malformed quoteSummary response for " + requested);
        }
        return MapperSupport.firstResult(summary.result(), summary.error(), "quoteSummary data", requested);
    }

    private static @Nullable CompanyProfile mapProfile(@Nullable AssetProfile p) {
        if (p == null) {
            return null;
        }
        var officers = p.companyOfficers() == null
                ? List.<CompanyOfficer>of()
                : p.companyOfficers().stream()
                        .map(o -> new CompanyOfficer(o.name(), o.title(), o.age(), o.totalPay()))
                        .toList();
        return new CompanyProfile(
                p.address1(), p.city(), p.state(), p.zip(), p.country(), p.phone(),
                MapperSupport.uri(p.website()), p.industry(), p.sector(),
                p.longBusinessSummary(), p.fullTimeEmployees(), officers);
    }

    private static Quote mapQuote(Result r, Symbol requested) {
        QuoteType qt = r.quoteType();
        Price price = r.price();
        SummaryDetail sd = r.summaryDetail();
        FinancialData fd = r.financialData();
        DefaultKeyStatistics ks = r.defaultKeyStatistics();
        String currencyCode = price != null ? price.currency() : from(sd, SummaryDetail::currency);
        Symbol symbol = MapperSupport.symbolOr(from(qt, QuoteType::symbol), requested);
        return new Quote(
                symbol,
                from(qt, QuoteType::longName),
                from(qt, QuoteType::shortName),
                from(qt, QuoteType::quoteType),
                from(qt, QuoteType::exchange),
                MapperSupport.currency(currencyCode),
                from(price, Price::marketState),
                new Quote.PriceSnapshot(
                        from(price, Price::regularMarketPrice),
                        from(price, Price::regularMarketChange),
                        from(price, Price::regularMarketChangePercent),
                        from(sd, SummaryDetail::previousClose),
                        from(sd, SummaryDetail::open),
                        from(sd, SummaryDetail::dayLow),
                        from(sd, SummaryDetail::dayHigh),
                        from(sd, SummaryDetail::volume),
                        from(sd, SummaryDetail::fiftyTwoWeekLow),
                        from(sd, SummaryDetail::fiftyTwoWeekHigh),
                        from(price, Price::marketCap)),
                new Quote.KeyStats(
                        from(sd, SummaryDetail::trailingPE),
                        from(ks, DefaultKeyStatistics::trailingEps),
                        from(ks, DefaultKeyStatistics::forwardEps),
                        from(ks, DefaultKeyStatistics::bookValue),
                        from(ks, DefaultKeyStatistics::priceToBook),
                        from(ks, DefaultKeyStatistics::beta),
                        from(ks, DefaultKeyStatistics::sharesOutstanding),
                        from(sd, SummaryDetail::dividendYield)),
                new Quote.AnalystSummary(
                        from(fd, FinancialData::targetMeanPrice),
                        from(fd, FinancialData::recommendationMean),
                        from(fd, FinancialData::recommendationKey),
                        from(fd, FinancialData::numberOfAnalystOpinions),
                        from(fd, FinancialData::totalRevenue),
                        from(fd, FinancialData::profitMargins)));
    }

    private static List<RecommendationPeriod> mapRecommendations(Result r) {
        if (r.recommendationTrend() == null || r.recommendationTrend().trend() == null) {
            return List.of();
        }
        return r.recommendationTrend().trend().stream()
                .map(t -> new RecommendationPeriod(
                        t.period(),
                        orZero(t.strongBuy()), orZero(t.buy()), orZero(t.hold()),
                        orZero(t.sell()), orZero(t.strongSell())))
                .toList();
    }

    private static List<UpgradeDowngrade> mapUpgrades(Result r) {
        if (r.upgradeDowngradeHistory() == null || r.upgradeDowngradeHistory().history() == null) {
            return List.of();
        }
        return r.upgradeDowngradeHistory().history().stream()
                .map(h -> new UpgradeDowngrade(
                        MapperSupport.epochSecond(h.epochGradeDate()),
                        h.firm(), h.toGrade(), h.fromGrade(), h.action()))
                .toList();
    }

    private static List<Instant> mapEarningsDates(Result r) {
        if (r.calendarEvents() == null || r.calendarEvents().earnings() == null
                || r.calendarEvents().earnings().earningsDate() == null) {
            return List.of();
        }
        return r.calendarEvents().earnings().earningsDate().stream()
                .map(Instant::ofEpochSecond)
                .toList();
    }

    private static List<SecFiling> mapSecFilings(Result r) {
        if (r.secFilings() == null || r.secFilings().filings() == null) {
            return List.of();
        }
        return r.secFilings().filings().stream()
                .map(f -> new SecFiling(
                        MapperSupport.epochSecond(f.epochDate()), f.type(), f.title(),
                        MapperSupport.uri(f.edgarUrl())))
                .toList();
    }

    private static int orZero(@Nullable Integer value) {
        return value != null ? value : 0;
    }
}
