package io.github.dimazigel.yfinance.service;

import io.github.dimazigel.yfinance.Tickers;
import io.github.dimazigel.yfinance.assembly.Payload;
import io.github.dimazigel.yfinance.assembly.Resolved;
import io.github.dimazigel.yfinance.assembly.Resolver;
import io.github.dimazigel.yfinance.assembly.build.CoreBuilder;
import io.github.dimazigel.yfinance.assembly.build.CryptoBuilder;
import io.github.dimazigel.yfinance.assembly.build.EquityBuilder;
import io.github.dimazigel.yfinance.assembly.build.EtfBuilder;
import io.github.dimazigel.yfinance.assembly.build.FutureBuilder;
import io.github.dimazigel.yfinance.assembly.build.MutualFundBuilder;
import io.github.dimazigel.yfinance.assembly.build.SimpleBuilders;
import io.github.dimazigel.yfinance.assembly.specs.CoreSpecs;
import io.github.dimazigel.yfinance.assembly.specs.SnapshotSpecs;
import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.batch.FanOut;
import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.batch.SkipReason;
import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.exception.YFinanceException;
import io.github.dimazigel.yfinance.http.RawQuoteClient;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Core;
import io.github.dimazigel.yfinance.instrument.Instrument;
import io.github.dimazigel.yfinance.instrument.Unclassified;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import tools.jackson.databind.JsonNode;

/**
 * Snapshot-depth instruments: one batched v7 request per {@value RawQuoteClient#CHUNK} distinct
 * symbols, classified by {@code quoteType}, with a per-symbol quoteSummary fallback only when the
 * v7 row alone leaves a guaranteed field short. A symbol that still can't fill its class's
 * guarantee after the fallback is returned as {@link Unclassified} rather than dropped; a symbol
 * Yahoo does not know at all is {@link SkipReason#UNKNOWN_SYMBOL}.
 *
 * <p>Each v7 chunk fails on its own: the symbols of a chunk whose request failed are
 * {@link Outcome.Failed}, the other chunks proceed. The fallbacks are fanned out through
 * {@link FanOut} with the configured concurrency rather than run one after another on the calling
 * thread, and the batch summary reports how many there were.
 */
public final class InstrumentService {

    private static final Logger LOG = LoggerFactory.getLogger(InstrumentService.class);

    private final RawQuoteClient client;
    private final Clock clock;
    private final int concurrency;

    /** Fallbacks run with {@link Tickers#DEFAULT_CONCURRENCY}. */
    public InstrumentService(RawQuoteClient client, Clock clock) {
        this(client, clock, Tickers.DEFAULT_CONCURRENCY);
    }

    /** {@code concurrency} bounds how many quoteSummary fallback requests are in flight at once. */
    public InstrumentService(RawQuoteClient client, Clock clock, int concurrency) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1");
        }
        this.client = client;
        this.clock = clock;
        this.concurrency = concurrency;
    }

    /** One batched v7 request per chunk, classifying each symbol into its asset class or downgrading it. */
    public Batch<Instrument> instruments(List<Symbol> symbols) {
        if (symbols.isEmpty()) {
            return new Batch<>(List.of());
        }
        try (var ignored = LogContext.scope("instruments", symbols)) {
            Instant now = clock.instant();
            List<Symbol> distinct = symbols.stream().distinct().toList();
            var rows = new HashMap<Symbol, JsonNode>();
            var chunkFailures = new HashMap<Symbol, YFinanceException>();
            fetchRows(distinct, rows, chunkFailures);

            var needingFallback = new ArrayList<Symbol>();
            for (Symbol symbol : distinct) {
                JsonNode row = rows.get(symbol);
                if (row != null && needsFallback(symbol, row)) {
                    needingFallback.add(symbol);
                }
            }
            Map<Symbol, Outcome<Optional<Map<String, JsonNode>>>> fallbacks = fetchFallbacks(needingFallback, rows);

            var outcomes = new ArrayList<Outcome<Instrument>>(symbols.size());
            for (Symbol symbol : symbols) {
                outcomes.add(outcome(symbol, rows.get(symbol), chunkFailures.get(symbol), fallbacks.get(symbol), now));
            }
            return summarised(new Batch<>(outcomes), needingFallback.size());
        }
    }

    /** One v7 request per chunk; a failed chunk fails only its own symbols. */
    private void fetchRows(List<Symbol> distinct, Map<Symbol, JsonNode> rows, Map<Symbol, YFinanceException> chunkFailures) {
        for (int i = 0; i < distinct.size(); i += RawQuoteClient.CHUNK) {
            List<Symbol> chunk = distinct.subList(i, Math.min(i + RawQuoteClient.CHUNK, distinct.size()));
            try {
                rows.putAll(client.quoteRows(chunk));
            } catch (YFinanceException e) {
                LOG.atDebug().addKeyValue("symbols", chunk.size()).log("v7 chunk of {} symbols failed: {}", chunk.size(), e.getMessage());
                for (Symbol symbol : chunk) {
                    chunkFailures.put(symbol, e);
                }
            }
        }
    }

    /** The quoteSummary fallbacks, fanned out; a fetch that throws becomes that symbol's {@link Outcome.Failed}. */
    private Map<Symbol, Outcome<Optional<Map<String, JsonNode>>>> fetchFallbacks(List<Symbol> symbols, Map<Symbol, JsonNode> rows) {
        var bySymbol = new HashMap<Symbol, Outcome<Optional<Map<String, JsonNode>>>>();
        if (symbols.isEmpty()) {
            return bySymbol;
        }
        Batch<Optional<Map<String, JsonNode>>> fetched = FanOut.run(symbols, concurrency, symbol -> {
            // On the worker thread: the per-symbol scope is opened here, where the HTTP call happens.
            try (var ignored = LogContext.scope("instruments", symbol)) {
                AssetClass target = target(rows.get(symbol)).orElse(AssetClass.UNCLASSIFIED);
                return Outcome.ok(symbol, client.modules(symbol, SnapshotSpecs.fallbackModules(target)));
            }
        });
        for (Outcome<Optional<Map<String, JsonNode>>> outcome : fetched.outcomes()) {
            bySymbol.put(outcome.symbol(), outcome);
        }
        return bySymbol;
    }

    private Outcome<Instrument> outcome(
            Symbol symbol,
            @Nullable JsonNode row,
            @Nullable YFinanceException chunkFailure,
            @Nullable Outcome<Optional<Map<String, JsonNode>>> fallback,
            Instant now) {
        if (chunkFailure != null) {
            return Outcome.failed(symbol, chunkFailure);
        }
        if (row == null) {
            return Outcome.skipped(symbol, SkipReason.UNKNOWN_SYMBOL, "not in Yahoo's quote response");
        }
        Optional<Map<String, JsonNode>> modules = Optional.empty();
        if (fallback != null) {
            switch (fallback) {
                case Outcome.Ok<Optional<Map<String, JsonNode>>> ok -> modules = ok.value();
                case Outcome.Failed<Optional<Map<String, JsonNode>>> failed -> {
                    return Outcome.failed(symbol, failed.error());
                }
                case Outcome.Skipped<Optional<Map<String, JsonNode>>> skipped -> {
                    return Outcome.skipped(symbol, skipped.reason(), skipped.detail());
                }
            }
        }
        try {
            return assemble(symbol, row, modules, now);
        } catch (YFinanceException e) {
            return Outcome.failed(symbol, e);
        } catch (RuntimeException e) {
            return Outcome.failed(symbol, new YFDataException("Failed to assemble " + symbol, e));
        }
    }

    /** INFO once per batch; a single-symbol lookup ({@code Ticker.instrument()}) is per-symbol detail, so DEBUG. */
    private static Batch<Instrument> summarised(Batch<Instrument> batch, int fallbacks) {
        LOG.atLevel(batch.size() > 1 ? Level.INFO : Level.DEBUG)
                .addKeyValue("fallbacks", fallbacks)
                .log("instruments: {}; fallbacks={}", batch.summary(), fallbacks);
        return batch;
    }

    /**
     * As {@link #instruments(List)}, narrowed to {@code as}: a symbol classified as another asset
     * class is {@link SkipReason#WRONG_ASSET_CLASS}, and a downgraded ({@link Unclassified}) symbol
     * is {@link SkipReason#DOWNGRADED}.
     */
    public <I extends Instrument> Batch<I> instruments(List<Symbol> symbols, Class<I> as) {
        var narrowed = new ArrayList<Outcome<I>>();
        for (Outcome<Instrument> o : instruments(symbols).outcomes()) {
            narrowed.add(switch (o) {
                case Outcome.Ok<Instrument> ok when as.isInstance(ok.value()) -> Outcome.ok(ok.symbol(), as.cast(ok.value()));
                case Outcome.Ok<Instrument> ok when ok.value() instanceof Unclassified u ->
                        Outcome.skipped(ok.symbol(), SkipReason.DOWNGRADED,
                                "missing " + u.missing() + " (reported " + u.reportedQuoteType() + ")");
                case Outcome.Ok<Instrument> ok -> Outcome.skipped(ok.symbol(), SkipReason.WRONG_ASSET_CLASS, ok.value().assetClass().name());
                case Outcome.Skipped<Instrument> s -> Outcome.skipped(s.symbol(), s.reason(), s.detail());
                case Outcome.Failed<Instrument> f -> Outcome.failed(f.symbol(), f.error());
            });
        }
        return new Batch<>(narrowed);
    }

    /** A single instrument. Throws {@link YFDataException} for a skip (e.g. an unknown symbol). */
    public Instrument instrument(Symbol symbol) {
        return instruments(List.of(symbol)).outcomes().getFirst().orElseThrow();
    }

    private static Optional<AssetClass> target(@Nullable JsonNode row) {
        return row == null ? Optional.empty() : AssetClass.fromQuoteType(row.path("quoteType").asString(""));
    }

    /** Whether the v7 row alone leaves a field its class guarantees unfilled, so quoteSummary is worth asking. */
    private static boolean needsFallback(Symbol symbol, JsonNode row) {
        AssetClass target = target(row).orElse(AssetClass.UNCLASSIFIED);
        var payload = new Payload(symbol, Optional.of(row), Map.of());
        return !Resolver.resolve(payload, SnapshotSpecs.forClass(target)).missingRequired().isEmpty();
    }

    private Outcome<Instrument> assemble(Symbol symbol, JsonNode row, Optional<Map<String, JsonNode>> modules, Instant now) {
        String reported = row.path("quoteType").asString("");
        Optional<AssetClass> attempted = AssetClass.fromQuoteType(reported);
        AssetClass target = attempted.orElse(AssetClass.UNCLASSIFIED);
        var specs = SnapshotSpecs.forClass(target);
        var payload = new Payload(symbol, Optional.of(row), modules.orElse(Map.of()));
        Resolved resolved = Resolver.resolve(payload, specs);
        Resolved coreOnly = Resolver.resolve(payload, CoreSpecs.CORE);
        if (!coreOnly.missingRequired().isEmpty()) {
            return Outcome.skipped(symbol, SkipReason.UNKNOWN_SYMBOL, "core incomplete: " + coreOnly.missingRequired());
        }
        if (attempted.isPresent() && resolved.missingRequired().isEmpty()) {
            return Outcome.ok(symbol, build(target, resolved, now));
        }
        Core core = CoreBuilder.build(coreOnly);
        List<String> missing = attempted.isPresent() ? resolved.missingRequired() : List.of();
        if (attempted.isPresent()) {
            LOG.atDebug().addKeyValue("missing", missing).log("{} downgraded from {}: missing {}", symbol, target, missing);
        }
        return Outcome.ok(symbol, new Unclassified(core, reported, attempted, missing, now));
    }

    private static Instrument build(AssetClass target, Resolved r, Instant now) {
        return switch (target) {
            case EQUITY -> EquityBuilder.build(r, now);
            case ETF -> EtfBuilder.build(r, now);
            case MUTUAL_FUND -> MutualFundBuilder.build(r, now);
            case INDEX -> SimpleBuilders.index(r, now);
            case FX -> SimpleBuilders.fxPair(r, now);
            case CRYPTO -> CryptoBuilder.build(r, now);
            case FUTURE -> FutureBuilder.build(r, now);
            case UNCLASSIFIED -> throw new IllegalStateException("UNCLASSIFIED is built as Unclassified, not here");
        };
    }
}
