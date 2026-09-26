package io.github.dimazigel.yfinance.service;

import io.github.dimazigel.yfinance.api.OptionsApi;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.mapper.OptionsMapper;
import io.github.dimazigel.yfinance.market.OptionChain;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Retrieves option chains. */
public final class OptionsService {

    private final OptionsApi api;

    public OptionsService(OptionsApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    /** The nearest expiration's chain, or empty when the underlying has no listed options. */
    public Optional<OptionChain> getOptionChain(Symbol symbol) {
        return getOptionChain(symbol, null);
    }

    /**
     * The chain for a specific expiration, or the nearest one when {@code expiration} is null.
     * Empty when the underlying has no listed options at all.
     */
    public Optional<OptionChain> getOptionChain(Symbol symbol, @Nullable Instant expiration) {
        try (var ignored = LogContext.scope("options", symbol)) {
            Long date = expiration != null ? expiration.getEpochSecond() : null;
            var response = api.options(symbol.value(), date);
            return OptionsMapper.toOptionChain(response, symbol);
        }
    }

    /** All available expiration dates for the underlying, or empty when it has no listed options. */
    public List<Instant> getExpirationDates(Symbol symbol) {
        return getOptionChain(symbol).map(OptionChain::expirationDates).orElseGet(List::of);
    }
}
