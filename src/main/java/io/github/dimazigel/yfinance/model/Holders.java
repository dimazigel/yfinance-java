package io.github.dimazigel.yfinance.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Ownership and insider data assembled from the holder-related quoteSummary modules. */
public record Holders(
        @Nullable MajorHoldersBreakdown breakdown,
        List<InstitutionalHolder> institutional,
        List<InstitutionalHolder> mutualFund,
        List<InsiderTransaction> insiderTransactions,
        List<InsiderRosterEntry> insiderRoster,
        @Nullable NetSharePurchaseActivity netSharePurchaseActivity) {

    public Holders {
        institutional = institutional == null ? List.of() : List.copyOf(institutional);
        mutualFund = mutualFund == null ? List.of() : List.copyOf(mutualFund);
        insiderTransactions = insiderTransactions == null ? List.of() : List.copyOf(insiderTransactions);
        insiderRoster = insiderRoster == null ? List.of() : List.copyOf(insiderRoster);
    }

    /** Aggregate ownership percentages. */
    public record MajorHoldersBreakdown(
            @Nullable BigDecimal insidersPercentHeld,
            @Nullable BigDecimal institutionsPercentHeld,
            @Nullable BigDecimal institutionsFloatPercentHeld,
            @Nullable Integer institutionsCount) {}

    /** A single institutional or mutual-fund holder. */
    public record InstitutionalHolder(
            @Nullable Instant reportDate,
            @Nullable String organization,
            @Nullable BigDecimal pctHeld,
            @Nullable Long position,
            @Nullable Long value,
            @Nullable BigDecimal pctChange) {}

    /** A single insider transaction. */
    public record InsiderTransaction(
            @Nullable Instant date,
            @Nullable String filerName,
            @Nullable String filerRelation,
            @Nullable String transactionText,
            @Nullable Long shares,
            @Nullable Long value) {}

    /** A named insider's current position, from the {@code insiderHolders} module. */
    public record InsiderRosterEntry(
            @Nullable String name,
            @Nullable String relation,
            @Nullable String latestTransactionDescription,
            @Nullable Instant latestTransactionDate,
            @Nullable Long positionDirect,
            @Nullable Instant positionDirectDate) {}

    /** Aggregated insider buy/sell activity over a trailing period (e.g. {@code 6m}). */
    public record NetSharePurchaseActivity(
            @Nullable String period,
            @Nullable Integer buyCount,
            @Nullable Long buyShares,
            @Nullable BigDecimal buyPercentInsiderShares,
            @Nullable Integer sellCount,
            @Nullable Long sellShares,
            @Nullable BigDecimal sellPercentInsiderShares,
            @Nullable Integer netCount,
            @Nullable Long netShares,
            @Nullable BigDecimal netPercentInsiderShares,
            @Nullable Long totalInsiderShares) {}
}
