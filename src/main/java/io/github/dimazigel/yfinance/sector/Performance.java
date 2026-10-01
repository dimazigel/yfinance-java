package io.github.dimazigel.yfinance.sector;

import java.math.BigDecimal;

/**
 * Price performance over five horizons, each a fraction ({@code 0.25} is +25 %).
 *
 * @param dayChange change in the regular session of the latest trading day
 * @param ytd change since the start of the year
 * @param oneYear change over one year
 * @param threeYear change over three years
 * @param fiveYear change over five years
 */
public record Performance(
        BigDecimal dayChange, BigDecimal ytd, BigDecimal oneYear, BigDecimal threeYear, BigDecimal fiveYear) {}
