package io.github.dimazigel.yfinance.assembly.build;

import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.detail.CryptoDetail;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/** {@link Resolved} → {@link CryptoDetail}. Callers must have checked {@code missingRequired()} first. */
public final class CryptoDetailBuilder {

    private CryptoDetailBuilder() {}

    public static CryptoDetail build(Resolved r, Symbol symbol, Instant fetchedAt) {
        return new CryptoDetail(
                symbol,
                r.string("name"),
                r.string("description"),
                URI.create(r.string("website")),
                r.date("startDate"),
                r.decimal("fullyDilutedValue"),
                r.optString("whitepaper").flatMap(Nodes::uri),   // optional URL: lenient, logged when dropped
                r.optString("twitter"),
                r.clusterPresent("proofOfWork") ? Optional.of(proofOfWork(r)) : Optional.empty(),
                fetchedAt);
    }

    private static CryptoDetail.ProofOfWork proofOfWork(Resolved r) {
        return new CryptoDetail.ProofOfWork(
                r.longValue("proofOfWork.blockNumber"),
                r.decimal("proofOfWork.blockReward"),
                r.decimal("proofOfWork.netHashesPerSecond"));
    }
}
