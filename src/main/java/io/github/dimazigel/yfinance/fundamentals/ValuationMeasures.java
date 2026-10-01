package io.github.dimazigel.yfinance.fundamentals;

import io.github.dimazigel.yfinance.instrument.QuoteCurrency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * An equity's valuation measures as of one period end: one column of the "Valuation Measures"
 * table on Yahoo's statistics page. Every measure is {@code Optional}: Yahoo may have a point for
 * some measures of a period and none for others.
 *
 * @param asOf the period end the measures are stated for (a quarter end or a fiscal year end)
 * @param currency the currency of {@code marketCap} and {@code enterpriseValue}; empty when the
 *     row has neither
 * @param marketCap market capitalisation
 * @param enterpriseValue enterprise value
 * @param trailingPE price to trailing-twelve-month earnings
 * @param forwardPE price to forecast earnings
 * @param pegRatio price/earnings to growth, five-year expected
 * @param priceToSales price to trailing-twelve-month revenue
 * @param priceToBook price to book value, most recent quarter
 * @param enterpriseToRevenue enterprise value to revenue
 * @param enterpriseToEbitda enterprise value to EBITDA
 */
public record ValuationMeasures(
        LocalDate asOf,
        Optional<QuoteCurrency> currency,
        Optional<BigDecimal> marketCap,
        Optional<BigDecimal> enterpriseValue,
        Optional<BigDecimal> trailingPE,
        Optional<BigDecimal> forwardPE,
        Optional<BigDecimal> pegRatio,
        Optional<BigDecimal> priceToSales,
        Optional<BigDecimal> priceToBook,
        Optional<BigDecimal> enterpriseToRevenue,
        Optional<BigDecimal> enterpriseToEbitda) {}
