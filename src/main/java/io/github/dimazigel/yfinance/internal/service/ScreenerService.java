package io.github.dimazigel.yfinance.internal.service;

import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.enums.PredefinedScreen;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.internal.api.ScreenerApi;
import io.github.dimazigel.yfinance.internal.dto.screener.ScreenerRequest;
import io.github.dimazigel.yfinance.internal.mapper.ScreenQueryMapper;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.screener.EquityScreenField;
import io.github.dimazigel.yfinance.screener.FundScreenField;
import io.github.dimazigel.yfinance.screener.ScreenOptions;
import io.github.dimazigel.yfinance.screener.ScreenQuery;
import io.github.dimazigel.yfinance.screener.ScreenResult;
import io.github.dimazigel.yfinance.valueobject.Isin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Yahoo's screener ({@code yf.screen} parity): a predefined screen or a custom query, one request
 * per page. The page's rows are v7 quote rows, so they are classified by
 * {@link InstrumentService#fromRows(List)}: no second request for the quotes and no quoteSummary
 * fallback — a row short of a field its class guarantees is downgraded, so a page is always
 * exactly one request.
 */
public final class ScreenerService {

    /** Yahoo's own default order for a custom query. */
    private static final String DEFAULT_SORT_FIELD = "ticker";

    private final ScreenerApi api;
    private final InstrumentService instruments;

    public ScreenerService(ScreenerApi api, InstrumentService instruments) {
        this.api = Objects.requireNonNull(api, "api");
        this.instruments = Objects.requireNonNull(instruments, "instruments");
    }

    public ScreenResult screen(PredefinedScreen screen, ScreenOptions options) {
        try (var ignored = LogContext.scope("screen")) {
            String sortField = options.sort().map(s -> s.field().key()).orElse(null);
            String sortType = options.sort().map(ScreenerService::sortType).orElse(null);
            return toResult(api.predefined(screen.wireValue(), options.size(), options.offset(), sortField, sortType));
        }
    }

    public ScreenResult screenEquities(ScreenQuery<EquityScreenField> query, ScreenOptions options) {
        return custom("EQUITY", query, options);
    }

    public ScreenResult screenFunds(ScreenQuery<FundScreenField> query, ScreenOptions options) {
        return custom("MUTUALFUND", query, options);
    }

    /**
     * Every equity listing of the security with this ISIN, one row per exchange, the most traded
     * first (so the primary listing leads), in one request: a screen on the {@code isin} field.
     * Yahoo's screener knows ISINs for equities only, and its rows do not carry the ISIN, so this
     * goes from ISIN to symbols and not back.
     */
    public Batch<Instrument> listings(Isin isin) {
        return screenEquities(
                ScreenQuery.eq(EquityScreenField.ISIN, isin.value()),
                ScreenOptions.defaults().withSize(ScreenOptions.MAX_SIZE).sortedBy(EquityScreenField.DAYVOLUME, false))
                .instruments();
    }

    private ScreenResult custom(String quoteType, ScreenQuery<?> query, ScreenOptions options) {
        try (var ignored = LogContext.scope("screen")) {
            var body = new ScreenerRequest(
                    options.offset(),
                    options.size(),
                    options.sort().map(s -> s.field().key()).orElse(DEFAULT_SORT_FIELD),
                    options.sort().map(ScreenerService::sortType).orElse("DESC"),
                    quoteType,
                    "",
                    "guid",
                    ScreenQueryMapper.toWire(query));
            return toResult(api.custom(body));
        }
    }

    private static String sortType(ScreenOptions.Sort sort) {
        return sort.ascending() ? "ASC" : "DESC";
    }

    private ScreenResult toResult(JsonNode response) {
        JsonNode finance = response.path("finance");
        JsonNode error = finance.path("error");
        if (error.isObject()) {
            throw new YFDataException("Yahoo screener error: " + error.path("description").asString(error.path("code").asString("unknown")));
        }
        JsonNode result = finance.path("result").path(0);
        if (!result.isObject()) {
            throw new YFDataException("Malformed screener response: no result");
        }
        var rows = new ArrayList<JsonNode>();
        result.path("quotes").forEach(rows::add);
        Batch<Instrument> page = instruments.fromRows(rows);
        return new ScreenResult(info(result), result.path("total").asInt(page.size()), result.path("start").asInt(0), page);
    }

    /** A saved screen describes itself; a custom query's result carries no id or title. */
    private static Optional<ScreenResult.Info> info(JsonNode result) {
        String id = result.path("id").asString("");
        String title = result.path("title").asString("");
        if (id.isBlank() || title.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ScreenResult.Info(
                id, title, result.path("description").asString(""), result.path("canonicalName").asString("")));
    }
}
