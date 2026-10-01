package io.github.dimazigel.yfinance.fundamentals;

import java.time.LocalDate;

/**
 * One reported shares-outstanding count. {@code date} is the UTC date of Yahoo's timestamp for the
 * report; Yahoo may report two values for the same date (an amendment), and both are kept, in wire
 * order.
 *
 * @param date the UTC date Yahoo reported this count for
 * @param shares the reported share count
 */
public record SharesOutstanding(LocalDate date, long shares) {}
