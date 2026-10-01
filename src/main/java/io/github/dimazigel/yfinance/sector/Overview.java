package io.github.dimazigel.yfinance.sector;

import java.math.BigDecimal;

/**
 * Size of a sector or an industry.
 *
 * @param companiesCount how many companies Yahoo counts in it
 * @param marketCap their combined market capitalisation, in US dollars
 * @param marketWeight its share of the level above, as a fraction: a sector's share of the whole
 *     market, an industry's share of its sector
 * @param employeeCount their combined number of employees
 * @param description Yahoo's description of what belongs in it
 * @param messageBoardId identifier of its message board on Yahoo Finance
 */
public record Overview(
        int companiesCount,
        BigDecimal marketCap,
        BigDecimal marketWeight,
        long employeeCount,
        String description,
        String messageBoardId) {}
