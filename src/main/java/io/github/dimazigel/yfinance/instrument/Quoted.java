package io.github.dimazigel.yfinance.instrument;

import java.util.Optional;

/** Classes Yahoo quotes with a bid/ask; present for almost all of them, hence Optional (design D5). */
public sealed interface Quoted permits Equity {
    Optional<TopOfBook> book();
}
