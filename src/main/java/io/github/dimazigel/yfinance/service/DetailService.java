package io.github.dimazigel.yfinance.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.build.CryptoDetailBuilder;
import io.github.dimazigel.yfinance.assembly.build.EquityDetailBuilder;
import io.github.dimazigel.yfinance.assembly.build.FundDetailBuilder;
import io.github.dimazigel.yfinance.assembly.specs.DetailSpecs;
import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.detail.CryptoDetail;
import io.github.dimazigel.yfinance.detail.EquityDetail;
import io.github.dimazigel.yfinance.detail.EtfDetail;
import io.github.dimazigel.yfinance.detail.MutualFundDetail;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.http.RawQuoteClient;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Crypto;
import io.github.dimazigel.yfinance.instrument.Equity;
import io.github.dimazigel.yfinance.instrument.Etf;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.instrument.MutualFund;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detail-depth instruments: for each already-classified instrument, one quoteSummary request
 * against its class's module set (see {@link DetailSpecs#modules(AssetClass)}), resolved into the
 * class's detail record. A symbol Yahoo no longer answers on quoteSummary — possible even for a
 * symbol just classified at snapshot depth, since quoteSummary and v7 are independent endpoints —
 * is {@link SkipReason#UNKNOWN_SYMBOL}; a symbol whose response is missing a field the class
 * guarantees is {@link SkipReason#MODULE_ABSENT}. Fan-out runs on virtual threads bounded by a
 * fixed concurrency limit (the {@code Tickers.fanOut} pattern); results come back in input order,
 * one per input instrument including duplicates.
 */
public final class DetailService {

    private static final Logger LOG = LoggerFactory.getLogger(DetailService.class);

    private final RawQuoteClient client;
    private final Clock clock;
    private final int concurrency;

    public DetailService(RawQuoteClient client, Clock clock, int concurrency) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1");
        }
        this.client = client;
        this.clock = clock;
        this.concurrency = concurrency;
    }

    public Outcome<EquityDetail> equity(Equity equity) {
        return equities(List.of(equity)).outcomes().getFirst();
    }

    public Batch<EquityDetail> equities(List<Equity> equities) {
        return fanOut(equities, AssetClass.EQUITY, EquityDetailBuilder::build);
    }

    public Outcome<EtfDetail> etf(Etf etf) {
        return etfs(List.of(etf)).outcomes().getFirst();
    }

    public Batch<EtfDetail> etfs(List<Etf> etfs) {
        return fanOut(etfs, AssetClass.ETF, (r, modules, symbol, fetchedAt) -> FundDetailBuilder.etf(r, symbol, fetchedAt));
    }

    public Outcome<MutualFundDetail> mutualFund(MutualFund fund) {
        return mutualFunds(List.of(fund)).outcomes().getFirst();
    }

    public Batch<MutualFundDetail> mutualFunds(List<MutualFund> funds) {
        return fanOut(funds, AssetClass.MUTUAL_FUND,
                (r, modules, symbol, fetchedAt) -> FundDetailBuilder.mutualFund(r, symbol, fetchedAt));
    }

    public Outcome<CryptoDetail> crypto(Crypto crypto) {
        return cryptos(List.of(crypto)).outcomes().getFirst();
    }

    public Batch<CryptoDetail> cryptos(List<Crypto> cryptos) {
        return fanOut(cryptos, AssetClass.CRYPTO, (r, modules, symbol, fetchedAt) -> CryptoDetailBuilder.build(r, symbol, fetchedAt));
    }

    private <D> Batch<D> fanOut(List<? extends Instrument> instruments, AssetClass expected, Assembler<D> assemble) {
        if (instruments.isEmpty()) {
            return new Batch<>(List.of());
        }
        List<Symbol> symbols = instruments.stream().map(Instrument::symbol).toList();
        try (var ignored = LogContext.scope("details", symbols)) {
            Instant fetchedAt = clock.instant();
            var permits = new Semaphore(concurrency);
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new ArrayList<Future<Outcome<D>>>(instruments.size());
                for (Instrument instrument : instruments) {
                    BiFunction<Resolved, Map<String, JsonNode>, D> build =
                            (r, modules) -> assemble.build(r, modules, instrument.symbol(), fetchedAt);
                    futures.add(pool.submit(() -> {
                        permits.acquire();
                        try {
                            return one(instrument, expected, build);
                        } finally {
                            permits.release();
                        }
                    }));
                }
                var outcomes = new ArrayList<Outcome<D>>(instruments.size());
                for (int i = 0; i < instruments.size(); i++) {
                    outcomes.add(join(instruments.get(i).symbol(), futures.get(i)));
                }
                return summarised(new Batch<>(outcomes));
            }
        }
    }

    private <D> Outcome<D> one(Instrument instrument, AssetClass expected, BiFunction<Resolved, Map<String, JsonNode>, D> build) {
        Symbol symbol = instrument.symbol();
        try {
            Optional<Map<String, JsonNode>> modules = client.modules(symbol, DetailSpecs.modules(expected));
            if (modules.isEmpty()) {
                return Outcome.skipped(symbol, SkipReason.UNKNOWN_SYMBOL, "quoteSummary 404");
            }
            Resolved r = Resolver.resolve(new Payload(symbol, Optional.empty(), modules.get()), DetailSpecs.forClass(expected));
            if (!r.missingRequired().isEmpty()) {
                LOG.atDebug().addKeyValue("missing", r.missingRequired())
                        .log("{} detail skipped: missing {}", symbol, r.missingRequired());
                return Outcome.skipped(symbol, SkipReason.MODULE_ABSENT, String.join(",", r.missingRequired()));
            }
            return Outcome.ok(symbol, build.apply(r, modules.get()));
        } catch (YFinanceException e) {
            return Outcome.failed(symbol, e);
        } catch (RuntimeException e) {
            return Outcome.failed(symbol, new YFDataException("Failed to assemble " + symbol, e));
        }
    }

    private static <D> Outcome<D> join(Symbol symbol, Future<Outcome<D>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.failed(symbol, new YFDataException("Interrupted fetching " + symbol, e));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            YFinanceException wrapped =
                    cause instanceof YFinanceException yf ? yf : new YFDataException("Failed to assemble " + symbol, cause);
            return Outcome.failed(symbol, wrapped);
        }
    }

    private static <D> Batch<D> summarised(Batch<D> batch) {
        LOG.atInfo().log("details: {}", batch.summary());
        return batch;
    }

    /** Builds the detail record for one instrument from its resolved fields and raw modules. */
    @FunctionalInterface
    private interface Assembler<D> {
        D build(Resolved resolved, Map<String, JsonNode> modules, Symbol symbol, Instant fetchedAt);
    }
}
