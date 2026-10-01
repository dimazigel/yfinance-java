package io.github.dimazigel.yfinance.internal.assembly.specs;

import static io.github.dimazigel.yfinance.internal.assembly.FieldSpec.clustered;
import static io.github.dimazigel.yfinance.internal.assembly.FieldSpec.optional;
import static io.github.dimazigel.yfinance.internal.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.internal.assembly.Unit.ISO_DATE;
import static io.github.dimazigel.yfinance.internal.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.internal.assembly.FieldSpec;
import java.util.List;

/** Crypto detail fields: identity from {@code assetProfile}, valuation from {@code summaryDetail}. */
public final class CryptoDetailSpecs {

    private CryptoDetailSpecs() {}

    public static final List<FieldSpec> DETAIL = List.of(
            required("name", RAW, "qs:assetProfile.name"),
            required("description", RAW, "qs:assetProfile.description"),
            required("website", RAW, "qs:assetProfile.website"),
            required("startDate", ISO_DATE, "qs:assetProfile.startDate"),
            required("fullyDilutedValue", RAW, "qs:summaryDetail.fullyDilutedValue"),
            optional("whitepaper", RAW, "qs:assetProfile.whitepaper"),
            optional("twitter", RAW, "qs:assetProfile.twitter"),
            clustered("proofOfWork", "proofOfWork.blockNumber", RAW, "qs:assetProfile.blockNumber"),
            clustered("proofOfWork", "proofOfWork.blockReward", RAW, "qs:assetProfile.blockReward"),
            clustered("proofOfWork", "proofOfWork.netHashesPerSecond", RAW, "qs:assetProfile.netHashesPerSecond"));
}
