package io.github.dimazigel.yfinance.detail.rows;

import java.time.Instant;
import java.util.Optional;

/** A single insider transaction, from the {@code insiderTransactions} module. */
public record InsiderTransaction(
        Optional<Instant> date,
        String filerName,
        Optional<String> filerRelation,
        Optional<String> transactionText,
        Optional<Long> shares,
        Optional<Long> value) {}
