package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.clustered;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.optional;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** Bid/ask top of book: bid, ask, bidSize, askSize. */
public final class BookSpecs {

    private BookSpecs() {}

    public static final List<FieldSpec> BOOK = List.of(
            clustered("book", "bid", RAW, "v7:bid", "qs:summaryDetail.bid"),
            clustered("book", "ask", RAW, "v7:ask", "qs:summaryDetail.ask"),
            optional("bidSize", RAW, "v7:bidSize", "qs:summaryDetail.bidSize"),
            optional("askSize", RAW, "v7:askSize", "qs:summaryDetail.askSize"));
}
