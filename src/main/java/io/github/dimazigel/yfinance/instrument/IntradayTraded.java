package io.github.dimazigel.yfinance.instrument;

/** Classes with a regular intraday session (open/high/low/volume): all but mutual funds. */
public sealed interface IntradayTraded permits Equity, Etf, Index, Crypto, FxPair, Future {
    Session session();
}
