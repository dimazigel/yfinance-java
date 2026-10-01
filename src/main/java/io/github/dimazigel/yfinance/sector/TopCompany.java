package io.github.dimazigel.yfinance.sector;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * One of the largest companies of a sector or an industry.
 *
 * @param symbol the company's ticker symbol
 * @param name the company's name
 * @param lastPrice last regular-session price
 * @param marketCap market capitalisation
 * @param marketWeight the company's share of the sector or industry, as a fraction
 * @param dayChangePercent change in the latest regular session, as a fraction
 * @param ytdReturn return since the start of the year, as a fraction
 * @param rating analysts' consensus, e.g. {@code Strong Buy}
 * @param targetPrice analysts' mean target price
 */
public record TopCompany(
        Symbol symbol,
        Optional<String> name,
        BigDecimal lastPrice,
        BigDecimal marketCap,
        BigDecimal marketWeight,
        BigDecimal dayChangePercent,
        Optional<BigDecimal> ytdReturn,
        Optional<String> rating,
        Optional<BigDecimal> targetPrice) {}
