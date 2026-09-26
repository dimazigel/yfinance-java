package io.github.dimazigel.yfinance.instrument;

import java.math.BigDecimal;
import java.util.Optional;

/** Best bid/ask. Sizes are optional: futures report them for ~80 % of contracts. */
public record TopOfBook(BigDecimal bid, BigDecimal ask, Optional<Long> bidSize, Optional<Long> askSize) {}
