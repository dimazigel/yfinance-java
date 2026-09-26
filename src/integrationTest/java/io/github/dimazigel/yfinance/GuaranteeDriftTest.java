package io.github.dimazigel.yfinance;

import io.github.dimazigel.yfinance.batch.Outcome;
import io.github.dimazigel.yfinance.instrument.AssetClass;
import io.github.dimazigel.yfinance.instrument.Unclassified;
import io.github.dimazigel.yfinance.valueobject.Symbol;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Weekly guarantee-drift detector (run by {@code live.yml}). Classifies the 282-symbol wide survey
 * (the seven lists in {@code docs/superpowers/specs/2026-09-26-yahoo-field-survey.md}, minus the
 * two dead symbols {@code RIDE}/{@code WISH}) in one batched request and asserts that, per class,
 * at least 99 % of the live instruments still classify without downgrading to {@link Unclassified}.
 *
 * <p>The guarantee is Intrinsic (design D5): a class's required fields are exactly the appendix's
 * "R" rows. Yahoo can stop sending one of them at any time without warning; when it does, affected
 * instruments downgrade gracefully to {@link Unclassified} rather than the library throwing (design
 * D2), and this test is what turns that red — with the field name, not just a percentage — so a
 * human notices the drift instead of every {@code Ticker.as(SomeClass.class)} call in production
 * quietly starting to fail.
 *
 * <p>One tolerated downgrade per class covers the odd preferred share or dead symbol that always
 * downgrades; a second one names the field Yahoo actually stopped sending.
 */
@Tag("live")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GuaranteeDriftTest {

    private YFinance yf;

    @BeforeAll
    void setUp() {
        yf = YFinance.create();
    }

    @AfterAll
    void tearDown() {
        yf.close();
    }

    @Test
    void everyClassStillClassifiesAtLeastNinetyNinePercent() {
        Map<AssetClass, List<Symbol>> byClass = readSurveySymbols();
        List<Symbol> allSymbols = byClass.values().stream().flatMap(List::stream).toList();

        var batch = yf.instruments(allSymbols);
        System.out.println("GuaranteeDriftTest: " + batch.summary());

        var soft = new SoftAssertions();
        for (var entry : byClass.entrySet()) {
            AssetClass assetClass = entry.getKey();
            List<Symbol> symbols = entry.getValue();

            long live = symbols.stream()
                    .filter(s -> batch.get(s).flatMap(Outcome::optional).isPresent())
                    .count();
            long downgraded = symbols.stream()
                    .filter(s -> batch.get(s).flatMap(Outcome::optional).filter(Unclassified.class::isInstance).isPresent())
                    .count();
            long failed = symbols.stream()
                    .filter(s -> batch.get(s).filter(Outcome.Failed.class::isInstance).isPresent())
                    .count();
            List<String> offenders = symbols.stream()
                    .filter(s -> batch.get(s).flatMap(Outcome::optional).filter(Unclassified.class::isInstance).isPresent())
                    .map(s -> s + "=" + ((Unclassified) batch.get(s).orElseThrow().orElseThrow()).missing())
                    .toList();

            System.out.println("  " + assetClass + ": live=" + live + "/" + symbols.size()
                    + " downgraded=" + downgraded + " failed=" + failed
                    + (offenders.isEmpty() ? "" : " offenders=" + offenders));

            soft.assertThat(failed)
                    .as("%s: %d transport failure(s) (a failure must never masquerade as \"nothing downgraded\")",
                            assetClass, failed)
                    .isZero();
            soft.assertThat(downgraded)
                    .as("%s downgraded: %s", assetClass, offenders)
                    .isLessThanOrEqualTo(Math.max(1, live / 100));
        }
        soft.assertAll();
    }

    /** Reads {@code CLASS SYMBOL} lines from the classpath resource copied verbatim from the spec. */
    private static Map<AssetClass, List<Symbol>> readSurveySymbols() {
        String resource = "/survey-symbols.txt";
        try (InputStream in = GuaranteeDriftTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " not found on the test classpath");
            }
            Map<AssetClass, List<Symbol>> byClass = new LinkedHashMap<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+", 2);
                AssetClass assetClass = AssetClass.fromQuoteType(parts[0])
                        .orElseThrow(() -> new IllegalStateException("Unknown class token: " + parts[0]));
                byClass.computeIfAbsent(assetClass, k -> new ArrayList<>()).add(Symbol.of(parts[1]));
            }
            return byClass;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
