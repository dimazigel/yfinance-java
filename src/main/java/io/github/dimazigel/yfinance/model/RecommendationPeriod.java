package io.github.dimazigel.yfinance.model;

import org.jspecify.annotations.Nullable;

/** Analyst recommendation counts for a single trailing period (e.g. {@code 0m}, {@code -1m}). */
public record RecommendationPeriod(
        @Nullable String period, int strongBuy, int buy, int hold, int sell, int strongSell) {}
