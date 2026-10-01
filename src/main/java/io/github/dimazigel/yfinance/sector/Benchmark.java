package io.github.dimazigel.yfinance.sector;

/**
 * The index Yahoo compares a sector or an industry against.
 *
 * @param name the benchmark's name, e.g. {@code S&P 500}
 * @param performance the benchmark's performance over the same horizons
 */
public record Benchmark(String name, Performance performance) {}
