package io.github.dimazigel.yfinance.detail.rows;

/** Analyst recommendation counts for a single trailing period (e.g. {@code 0m}, {@code -1m}). */
public record RecommendationPeriod(String period, int strongBuy, int buy, int hold, int sell, int strongSell) {}
