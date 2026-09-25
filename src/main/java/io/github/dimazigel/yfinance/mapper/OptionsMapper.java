package io.github.dimazigel.yfinance.mapper;

import io.github.dimazigel.yfinance.dto.options.OptionChainResponse.Contract;
import io.github.dimazigel.yfinance.dto.options.OptionChainResponse.OptionsByExpiration;
import io.github.dimazigel.yfinance.dto.options.OptionChainResponse.Result;
import io.github.dimazigel.yfinance.dto.options.OptionChainResponse;
import io.github.dimazigel.yfinance.enums.OptionType;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.model.OptionChain;
import io.github.dimazigel.yfinance.model.OptionContract;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps the raw options response into the {@link OptionChain} model. */
public final class OptionsMapper {

    private OptionsMapper() {}

    public static OptionChain toOptionChain(OptionChainResponse response, Symbol requested) {
        var oc = response.optionChain();
        if (oc == null) {
            throw new YFDataException("Malformed options response for " + requested);
        }
        Result result = MapperSupport.firstResult(oc.result(), oc.error(), "options data", requested);
        List<Instant> expirations = result.expirationDates() == null
                ? List.of()
                : result.expirationDates().stream().map(Instant::ofEpochSecond).toList();

        OptionsByExpiration options = result.options() != null && !result.options().isEmpty()
                ? result.options().getFirst()
                : null;
        Instant expiration = options != null ? MapperSupport.epochSecond(options.expirationDate()) : null;

        return new OptionChain(
                MapperSupport.symbolOr(result.underlyingSymbol(), requested),
                expirations,
                expiration,
                mapContracts(options != null ? options.calls() : null, OptionType.CALL),
                mapContracts(options != null ? options.puts() : null, OptionType.PUT));
    }

    private static List<OptionContract> mapContracts(@Nullable List<Contract> contracts, OptionType type) {
        if (contracts == null) {
            return List.of();
        }
        return contracts.stream()
                .map(c -> new OptionContract(
                        c.contractSymbol(), type, c.strike(), MapperSupport.currency(c.currency()),
                        c.lastPrice(), c.bid(), c.ask(), c.change(), c.percentChange(),
                        c.volume(), c.openInterest(), c.impliedVolatility(),
                        Boolean.TRUE.equals(c.inTheMoney()), c.contractSize(),
                        MapperSupport.epochSecond(c.lastTradeDate()),
                        MapperSupport.epochSecond(c.expiration())))
                .toList();
    }
}
