package io.github.dimazigel.yfinance.detail.rows;

import java.time.Instant;
import java.util.Optional;

/** A named insider's current position, from the {@code insiderHolders} module. */
public record InsiderHolder(
        String name,
        Optional<String> relation,
        Optional<String> latestTransactionDescription,
        Optional<Instant> latestTransactionDate,
        Optional<Long> positionDirect,
        Optional<Instant> positionDirectDate) {}
