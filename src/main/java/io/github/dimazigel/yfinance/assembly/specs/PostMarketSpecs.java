package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.clustered;
import static io.github.dimazigel.yfinance.assembly.Unit.EPOCH_SECONDS;
import static io.github.dimazigel.yfinance.assembly.Unit.PERCENT;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** After-hours trading: postMarketPrice, postMarketChange, postMarketChangePercent, postMarketTime. */
public final class PostMarketSpecs {

    private PostMarketSpecs() {}

    public static final List<FieldSpec> POST_MARKET = List.of(
            clustered("postMarket", "postMarketPrice", RAW, "v7:postMarketPrice", "qs:price.postMarketPrice"),
            clustered("postMarket", "postMarketChange", RAW, "v7:postMarketChange", "qs:price.postMarketChange"),
            clustered("postMarket", "postMarketChangePercent", PERCENT, "v7:postMarketChangePercent", "qs:price.postMarketChangePercent"),
            clustered("postMarket", "postMarketTime", EPOCH_SECONDS, "v7:postMarketTime", "qs:price.postMarketTime"));
}
