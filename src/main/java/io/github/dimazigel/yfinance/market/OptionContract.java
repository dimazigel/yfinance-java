package io.github.dimazigel.yfinance.market;

import io.github.dimazigel.yfinance.enums.OptionType;
import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * A single listed option contract (a call or a put) at one strike and expiration.
 *
 * <p>{@code contractSymbol}, {@code type}, {@code strike} and {@code expiration} identify the
 * contract and are always present by construction. The remaining components follow a per-row
 * survey of 987 live contracts (6 underlyings: AAPL at two expirations, SPY, {@code ^SPX}, GLD,
 * PLUG, TSLA): a key present in 100 % of rows is non-null here; {@code bid} (99.7 %), {@code
 * openInterest} (97.2 %) and {@code volume} (95.4 %) fell short and are {@link Optional}.
 *
 * @param currency          the currency the contract's prices are quoted in
 * @param bid               the current bid, absent for contracts with no resting bid
 * @param openInterest      open interest, absent when Yahoo has not reported any for the contract
 * @param volume            trading volume, absent when Yahoo has not reported any for the contract
 */
public record OptionContract(
        String contractSymbol,
        OptionType type,
        BigDecimal strike,
        Instant expiration,
        QuoteCurrency currency,
        BigDecimal lastPrice,
        BigDecimal change,
        BigDecimal percentChange,
        BigDecimal ask,
        String contractSize,
        Instant lastTradeDate,
        BigDecimal impliedVolatility,
        boolean inTheMoney,
        Optional<BigDecimal> bid,
        Optional<Long> openInterest,
        Optional<Long> volume) {}
