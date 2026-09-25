package io.github.dimazigel.yfinance.service;

import io.github.dimazigel.yfinance.api.OptionsApi;
import io.github.dimazigel.yfinance.mapper.OptionsMapper;
import io.github.dimazigel.yfinance.model.OptionChain;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Retrieves option chains. */
public final class OptionsService {

    private final OptionsApi api;

    public OptionsService(OptionsApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    /** The nearest expiration's chain. */
    public OptionChain getOptionChain(Symbol symbol) {
        return getOptionChain(symbol, null);
    }

    /** The chain for a specific expiration, or the nearest one when {@code expiration} is null. */
    public OptionChain getOptionChain(Symbol symbol, @Nullable Instant expiration) {
        Long date = expiration != null ? expiration.getEpochSecond() : null;
        var response = api.options(symbol.value(), date);
        return OptionsMapper.toOptionChain(response, symbol);
    }

    /** All available expiration dates for the underlying. */
    public List<Instant> getExpirationDates(Symbol symbol) {
        return getOptionChain(symbol).expirationDates();
    }
}
