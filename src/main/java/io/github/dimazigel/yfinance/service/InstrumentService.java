package io.github.dimazigel.yfinance.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Snapshot-depth instruments: one batched v7 request per call, classified by {@code quoteType},
 * with a per-symbol quoteSummary fallback only when the v7 row alone leaves a guaranteed field
 * short. A symbol that still can't fill its class's guarantee after the fallback is returned as
 * {@link Unclassified} rather than dropped; a symbol Yahoo does not know at all is {@link
 * SkipReason#UNKNOWN_SYMBOL}.
 */
public final class InstrumentService {

    private static final Logger LOG = LoggerFactory.getLogger(InstrumentService.class);

    private final RawQuoteClient client;
    private final Clock clock;

    public InstrumentService(RawQuoteClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    /** One batched v7 request, classifying each symbol into its asset class or downgrading it. */
    public Batch<Instrument> instruments(List<Symbol> symbols) {
        if (symbols.isEmpty()) {
            return new Batch<>(List.of());
        }
        try (var ignored = LogContext.scope("instruments", symbols)) {
            Instant now = clock.instant();
            Map<Symbol, JsonNode> rows;
            try {
                rows = client.quoteRows(symbols);
            } catch (YFinanceException e) {
                return summarised(new Batch<>(symbols.stream().<Outcome<Instrument>>map(s -> Outcome.failed(s, e)).toList()));
            }
            var outcomes = new ArrayList<Outcome<Instrument>>(symbols.size());
            for (Symbol symbol : symbols) {
                JsonNode row = rows.get(symbol);
                if (row == null) {
                    outcomes.add(Outcome.skipped(symbol, SkipReason.UNKNOWN_SYMBOL, "not in Yahoo's quote response"));
                    continue;
                }
                try {
                    outcomes.add(assemble(symbol, row, now));
                } catch (YFinanceException e) {
                    outcomes.add(Outcome.failed(symbol, e));
                } catch (RuntimeException e) {
                    outcomes.add(Outcome.failed(symbol, new YFDataException("Failed to assemble " + symbol, e)));
                }
            }
            return summarised(new Batch<>(outcomes));
        }
    }

    private static Batch<Instrument> summarised(Batch<Instrument> batch) {
        LOG.atInfo().log("instruments: {}", batch.summary());
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

    private Outcome<Instrument> assemble(Symbol symbol, JsonNode row, Instant now) {
        String reported = row.path("quoteType").asText("");
        Optional<AssetClass> attempted = AssetClass.fromQuoteType(reported);
        AssetClass target = attempted.orElse(AssetClass.UNCLASSIFIED);
        var specs = SnapshotSpecs.forClass(target);
        var payload = new Payload(symbol, Optional.of(row), Map.of());
        Resolved resolved = Resolver.resolve(payload, specs);
        if (!resolved.missingRequired().isEmpty()) {
            Optional<Map<String, JsonNode>> modules = client.modules(symbol, SnapshotSpecs.fallbackModules(target));
            if (modules.isPresent()) {
                payload = payload.withModules(modules.get());
                resolved = Resolver.resolve(payload, specs);
            }
        }
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
