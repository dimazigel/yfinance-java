package io.github.dimazigel.yfinance.mapper;

import io.github.dimazigel.yfinance.dto.options.OptionChainResponse;
import io.github.dimazigel.yfinance.dto.options.OptionChainResponse.Contract;
import io.github.dimazigel.yfinance.dto.options.OptionChainResponse.OptionsByExpiration;
import io.github.dimazigel.yfinance.dto.options.OptionChainResponse.Result;
import io.github.dimazigel.yfinance.enums.OptionType;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import io.github.dimazigel.yfinance.market.OptionChain;
import io.github.dimazigel.yfinance.market.OptionContract;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps the raw options response into the {@link OptionChain} model. */
public final class OptionsMapper {

    private static final Logger LOG = LoggerFactory.getLogger(OptionsMapper.class);

    private OptionsMapper() {}

    /** Empty when the underlying has no listed options at all ({@code expirationDates} is empty). */
    public static Optional<OptionChain> toOptionChain(OptionChainResponse response, Symbol requested) {
        var oc = response.optionChain();
        if (oc == null) {
            throw new YFDataException("Malformed options response for " + requested);
        }
        Result result = MapperSupport.firstResult(oc.result(), oc.error(), "options data", requested);

        List<Instant> expirationDates = result.expirationDates() == null
                ? List.of()
                : result.expirationDates().stream().map(Instant::ofEpochSecond).toList();
        if (expirationDates.isEmpty()) {
            return Optional.empty();
        }

        OptionsByExpiration options = result.options() != null && !result.options().isEmpty()
                ? result.options().getFirst()
                : null;
        if (options == null) {
            // expirationDates is non-empty, so a chain is promised; a missing bucket for it is drift.
            throw new YFDataException("Options data incomplete for " + requested + ": no contracts for a listed expiration");
        }
        Instant expiration = MapperSupport.epochSecond(options.expirationDate());
        if (expiration == null) {
            throw new YFDataException("Options data incomplete for " + requested + ": expiration missing for a listed chain");
        }

        MappedContracts calls = mapContracts(options.calls(), OptionType.CALL);
        MappedContracts puts = mapContracts(options.puts(), OptionType.PUT);
        int dropped = calls.dropped() + puts.dropped();
        int total = calls.total() + puts.total();
        if (dropped > 0) {
            LOG.atDebug().addKeyValue("dropped", dropped).addKeyValue("total", total)
                    .log("Dropped {} of {} contracts without a complete required field", dropped, total);
        }

        return Optional.of(new OptionChain(
                MapperSupport.symbolOr(result.underlyingSymbol(), requested),
                expirationDates,
                expiration,
                calls.contracts(),
                puts.contracts()));
    }

    private record MappedContracts(List<OptionContract> contracts, int total, int dropped) {}

    private static MappedContracts mapContracts(@Nullable List<Contract> contracts, OptionType type) {
        if (contracts == null || contracts.isEmpty()) {
            return new MappedContracts(List.of(), 0, 0);
        }
        var mapped = new ArrayList<OptionContract>(contracts.size());
        int dropped = 0;
        for (Contract c : contracts) {
            OptionContract contract = toContract(c, type);
            if (contract == null) {
                dropped++;
            } else {
                mapped.add(contract);
            }
        }
        return new MappedContracts(List.copyOf(mapped), contracts.size(), dropped);
    }

    /**
     * {@code null} when a field the per-row survey found in 100 % of live contracts is missing here;
     * the caller drops the contract and counts it toward the chain's DEBUG summary.
     */
    private static @Nullable OptionContract toContract(Contract c, OptionType type) {
        String contractSymbol = c.contractSymbol();
        BigDecimal strike = c.strike();
        Instant expiration = MapperSupport.epochSecond(c.expiration());
        String currencyCode = c.currency();
        BigDecimal lastPrice = c.lastPrice();
        BigDecimal change = c.change();
        BigDecimal percentChange = c.percentChange();
        BigDecimal ask = c.ask();
        String contractSize = c.contractSize();
        Instant lastTradeDate = MapperSupport.epochSecond(c.lastTradeDate());
        BigDecimal impliedVolatility = c.impliedVolatility();
        Boolean inTheMoney = c.inTheMoney();

        if (contractSymbol == null || strike == null || expiration == null
                || currencyCode == null || currencyCode.isBlank()
                || lastPrice == null || change == null || percentChange == null || ask == null
                || contractSize == null || lastTradeDate == null || impliedVolatility == null
                || inTheMoney == null) {
            return null;
        }

        return new OptionContract(
                contractSymbol, type, strike, expiration,
                QuoteCurrency.of(currencyCode), lastPrice, change, percentChange, ask, contractSize,
                lastTradeDate, impliedVolatility, inTheMoney,
                Optional.ofNullable(c.bid()), Optional.ofNullable(c.openInterest()), Optional.ofNullable(c.volume()));
    }
}
