package io.github.dimazigel.yfinance.detail;

import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Cryptocurrency detail: identity, launch date and (for proof-of-work coins) mining stats,
 * assembled from the crypto quoteSummary modules ({@code assetProfile}, {@code summaryDetail}).
 */
public record CryptoDetail(
        Symbol symbol,
        String name,
        String description,
        URI website,
        LocalDate startDate,
        BigDecimal fullyDilutedValue,
        Optional<URI> whitepaper,
        Optional<String> twitter,
        Optional<ProofOfWork> proofOfWork,
        Instant fetchedAt) {

    /** Mining stats Yahoo reports for proof-of-work coins; absent for proof-of-stake coins. */
    public record ProofOfWork(long blockNumber, BigDecimal blockReward, BigDecimal netHashesPerSecond) {}
}
