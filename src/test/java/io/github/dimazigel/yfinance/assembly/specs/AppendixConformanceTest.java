package io.github.dimazigel.yfinance.assembly.specs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import io.github.dimazigel.yfinance.assembly.Kind;
import io.github.dimazigel.yfinance.assembly.Unit;
import io.github.dimazigel.yfinance.assembly.WirePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Compares every in-code {@code FieldSpec} table under {@code assembly.specs} against Appendix A
 * ({@code docs/superpowers/specs/2026-09-26-typed-instrument-model-appendix.md}), the normative
 * field list for the typed instrument model (design doc, Task 18).
 *
 * <p>For each appendix table this test parses the {@code | `name` | kind | type | sources | unit |
 * coverage | notes |} rows into {@code (name, kind, cluster, paths, unit)} tuples and asserts that
 * the corresponding in-code list has exactly the same {@code (name, kind, cluster)} set, that each
 * field's wire paths resolve in the same precedence order (rendered {@code source:path}), and that
 * the <em>effective unit per path</em> agrees: the appendix's {@code unit} column is the field's
 * base unit (blank = {@code RAW}) and a {@code \|PERCENT} suffix on a source overrides it for that
 * source only, exactly as {@link FieldSpec#unit()} and {@link WirePath#unit()} do in code. The unit
 * check exists because the appendix twice carried a wrong unit into code (Task 7's v7 fund percents;
 * the final review's {@code qs:price.regularMarketChangePercent}, a fraction the code divided by
 * 100 again). A row whose name uses the appendix's {@code .*} or {@code .\{a,b\}} shorthand (e.g.
 * {@code profile.governance.*}) expands to a prefix check: every code {@link FieldSpec} whose name
 * starts with that prefix must carry the row's kind and cluster; paths and units are not checked
 * for such rows, since the appendix does not spell out one precedence chain per member.
 */
class AppendixConformanceTest {

    private static final Path APPENDIX = Path.of("docs/superpowers/specs/2026-09-26-typed-instrument-model-appendix.md");
    private static final Pattern CLUSTER_KIND = Pattern.compile("C:(\\w+)(\\(R\\))?");
    /** GFM needs the {@code |} of a unit suffix escaped inside a table cell; unescape before splitting cells. */
    private static final String ESCAPED_PIPE = "\\|";
    private static final char PIPE_PLACEHOLDER = '\u0001';
    private static final int NAME = 0;
    private static final int KIND = 1;
    private static final int SOURCES = 3;
    private static final int UNIT = 4;

    private static List<String> lines;

    @BeforeAll
    static void loadAppendix() throws IOException {
        lines = Files.readAllLines(APPENDIX);
    }

    /**
     * One parsed appendix row: a field name (possibly a {@code .*}/{@code .\{...\}} shorthand), its
     * kind, cluster, wire paths (without unit suffixes) and the effective unit of each path.
     */
    private record AppendixRow(String name, Kind kind, Optional<String> cluster, List<String> paths, List<Unit> units) {

        boolean isShorthand() {
            return name.contains(".*") || name.contains(".{");
        }

        /** The literal prefix a shorthand row expands to, e.g. {@code "profile.governance."}. */
        String prefix() {
            int star = name.indexOf(".*");
            if (star >= 0) {
                return name.substring(0, star + 1);
            }
            int brace = name.indexOf(".{");
            if (brace >= 0) {
                return name.substring(0, brace + 1);
            }
            throw new IllegalStateException("not a shorthand row: " + name);
        }
    }

    // ---- markdown parsing -------------------------------------------------

    /** Parses every field row under the given heading (matched by prefix) up to the next {@code ### } heading. */
    private static List<AppendixRow> section(String headingPrefix) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("### ") && line.substring(4).startsWith(headingPrefix)) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            throw new IllegalStateException("Appendix heading not found: " + headingPrefix);
        }
        var rows = new ArrayList<AppendixRow>();
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("### ")) {
                break;
            }
            parseRow(line).ifPresent(rows::add);
        }
        return rows;
    }

    /** Combines several sections' rows, for a code list assembled from several {@code FieldSpec} tables. */
    private static List<AppendixRow> sections(String... headingPrefixes) {
        var rows = new ArrayList<AppendixRow>();
        for (String heading : headingPrefixes) {
            rows.addAll(section(heading));
        }
        return rows;
    }

    private static Optional<AppendixRow> parseRow(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("|") || !trimmed.endsWith("|") || trimmed.length() < 2) {
            return Optional.empty();
        }
        List<String> cells = cells(trimmed);
        if (cells.size() < 4) {
            return Optional.empty();
        }
        String name = unbacktick(cells.get(NAME));
        if (name.isEmpty() || name.equals("field") || name.chars().allMatch(c -> c == '-')) {
            return Optional.empty(); // header or separator row
        }
        if (cells.size() <= UNIT) {
            throw new IllegalStateException("Field row without a unit column: " + line);
        }
        Kind kind;
        Optional<String> cluster;
        String kindCell = cells.get(KIND).trim();
        Matcher m = CLUSTER_KIND.matcher(kindCell);
        if (m.matches()) {
            cluster = Optional.of(m.group(1));
            kind = m.group(2) != null ? Kind.REQUIRED : Kind.OPTIONAL;
        } else {
            cluster = Optional.empty();
            kind = switch (kindCell) {
                case "R" -> Kind.REQUIRED;
                case "O" -> Kind.OPTIONAL;
                case "L" -> Kind.LIST;
                default -> throw new IllegalStateException("Unparseable kind '" + kindCell + "' in row: " + line);
            };
        }
        List<String> sources = Arrays.stream(cells.get(SOURCES).split("→"))
                .map(AppendixConformanceTest::unbacktick)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        String unitCell = unbacktick(cells.get(UNIT));
        Unit baseUnit = unitCell.isEmpty() ? Unit.RAW : Unit.valueOf(unitCell);
        var paths = new ArrayList<String>();
        var units = new ArrayList<Unit>();
        for (String source : sources) {
            int bar = source.indexOf('|');
            paths.add(bar < 0 ? source : source.substring(0, bar));
            units.add(bar < 0 ? baseUnit : Unit.valueOf(source.substring(bar + 1)));
        }
        return Optional.of(new AppendixRow(name, kind, cluster, List.copyOf(paths), List.copyOf(units)));
    }

    private static List<String> cells(String tableLine) {
        String body = tableLine.substring(1, tableLine.length() - 1).replace(ESCAPED_PIPE, String.valueOf(PIPE_PLACEHOLDER));
        return Arrays.stream(body.split("\\|", -1)).map(c -> c.replace(PIPE_PLACEHOLDER, '|')).toList();
    }

    private static String unbacktick(String cell) {
        String s = cell.trim();
        if (s.startsWith("`") && s.endsWith("`") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s.trim();
    }

    // ---- comparison ---------------------------------------------------------

    /**
     * Asserts that {@code actual} has exactly the {@code (name, kind, cluster)} set the appendix
     * {@code expected} rows describe, and that exact-name rows' wire paths resolve in the same
     * order with the same effective unit per path. Collects every discrepancy into one readable
     * failure per table instead of stopping at the first one.
     */
    private static void verify(String table, List<FieldSpec> actual, List<AppendixRow> expected) {
        Map<String, FieldSpec> actualByName = new LinkedHashMap<>();
        for (FieldSpec spec : actual) {
            actualByName.put(spec.name(), spec);
        }

        var problems = new ArrayList<String>();
        Set<String> matchedCodeNames = new HashSet<>();

        for (AppendixRow row : expected) {
            if (row.isShorthand()) {
                String prefix = row.prefix();
                List<FieldSpec> matches = actual.stream().filter(s -> s.name().startsWith(prefix)).toList();
                if (matches.isEmpty()) {
                    problems.add("appendix row '" + row.name() + "' (prefix '" + prefix + "') matches no code field");
                    continue;
                }
                for (FieldSpec spec : matches) {
                    matchedCodeNames.add(spec.name());
                    if (spec.kind() != row.kind()) {
                        problems.add("field '" + spec.name() + "' (under appendix row '" + row.name() + "'): appendix kind "
                                + row.kind() + " vs code kind " + spec.kind());
                    }
                    if (!spec.cluster().equals(row.cluster())) {
                        problems.add("field '" + spec.name() + "' (under appendix row '" + row.name() + "'): appendix cluster "
                                + row.cluster() + " vs code cluster " + spec.cluster());
                    }
                }
            } else {
                FieldSpec spec = actualByName.get(row.name());
                if (spec == null) {
                    problems.add("appendix row '" + row.name() + "' (kind " + row.kind() + ") has no matching code field");
                    continue;
                }
                matchedCodeNames.add(spec.name());
                if (spec.kind() != row.kind()) {
                    problems.add("field '" + row.name() + "': appendix kind " + row.kind() + " vs code kind " + spec.kind());
                }
                if (!spec.cluster().equals(row.cluster())) {
                    problems.add("field '" + row.name() + "': appendix cluster " + row.cluster() + " vs code cluster " + spec.cluster());
                }
                List<String> actualPaths = spec.paths().stream().map(WirePath::toString).toList();
                if (!actualPaths.equals(row.paths())) {
                    problems.add("field '" + row.name() + "': appendix paths " + row.paths() + " vs code paths " + actualPaths);
                }
                List<Unit> actualUnits = spec.paths().stream().map(p -> p.unit().orElse(spec.unit())).toList();
                if (!actualUnits.equals(row.units())) {
                    problems.add("field '" + row.name() + "': appendix effective units " + row.units()
                            + " vs code effective units " + actualUnits + " (paths " + actualPaths + ")");
                }
            }
        }

        for (FieldSpec spec : actual) {
            if (!matchedCodeNames.contains(spec.name())) {
                problems.add("code field '" + spec.name() + "' (kind " + spec.kind() + ") has no matching appendix row");
            }
        }

        assertThat(problems).as("%s: appendix vs code mismatches", table).isEmpty();
    }

    // ---- universal building blocks -----------------------------------------

    @Test
    void core() {
        verify("Universal core", CoreSpecs.CORE, section("Universal core"));
    }

    @Test
    void session() {
        verify("Session", SessionSpecs.SESSION, section("Session"));
    }

    @Test
    void book() {
        verify("TopOfBook cluster", BookSpecs.BOOK, section("TopOfBook cluster"));
    }

    @Test
    void postMarket() {
        // No dedicated appendix heading: the postMarket cluster is written out identically inside
        // each class's own snapshot table. "Equity — snapshot" is the reference copy.
        List<AppendixRow> postMarketRows = section("Equity — snapshot").stream()
                .filter(r -> r.name().startsWith("postMarket"))
                .toList();
        verify("PostMarket (from Equity — snapshot)", PostMarketSpecs.POST_MARKET, postMarketRows);
    }

    // ---- per-class snapshot and detail tables ------------------------------

    @Test
    void equitySnapshot() {
        verify("Equity — snapshot (incl. core/session/book)", EquitySpecs.SNAPSHOT,
                sections("Universal core", "Session", "TopOfBook cluster", "Equity — snapshot"));
    }

    @Test
    void equityDetail() {
        verify("Equity — detail", EquityDetailSpecs.DETAIL, section("Equity — detail"));
    }

    @Test
    void etfSnapshot() {
        verify("Etf — snapshot (incl. core/session/book)", EtfSpecs.SNAPSHOT,
                sections("Universal core", "Session", "TopOfBook cluster", "Etf — snapshot"));
    }

    @Test
    void etfDetail() {
        verify("Etf — detail (incl. shared fund detail)", EtfDetailSpecs.DETAIL,
                sections("Fund detail shared by Etf and MutualFund", "Etf — detail"));
    }

    @Test
    void fundDetailShared() {
        verify("Fund detail shared by Etf and MutualFund", FundDetailSpecs.COMMON,
                section("Fund detail shared by Etf and MutualFund"));
    }

    @Test
    void mutualFundSnapshot() {
        verify("MutualFund — snapshot (incl. core)", MutualFundSpecs.SNAPSHOT,
                sections("Universal core", "MutualFund — snapshot"));
    }

    @Test
    void mutualFundDetail() {
        verify("MutualFund — detail (incl. shared fund detail)", MutualFundDetailSpecs.DETAIL,
                sections("Fund detail shared by Etf and MutualFund", "MutualFund — detail"));
    }

    @Test
    void simpleIndexAndFxPair() {
        // Appendix: "Universal core + Session + TopOfBook. No class-specific fields, no detail tier."
        verify("Index and FxPair (core/session/book only)", SimpleSpecs.SIMPLE,
                sections("Universal core", "Session", "TopOfBook cluster"));
    }

    @Test
    void cryptoSnapshot() {
        verify("Crypto — snapshot (incl. core/session)", CryptoSpecs.SNAPSHOT,
                sections("Universal core", "Session", "Crypto — snapshot"));
    }

    @Test
    void cryptoDetail() {
        verify("Crypto — detail", CryptoDetailSpecs.DETAIL, section("Crypto — detail"));
    }

    @Test
    void futureSnapshot() {
        verify("Future — snapshot (incl. core/session/book)", FutureSpecs.SNAPSHOT,
                sections("Universal core", "Session", "TopOfBook cluster", "Future — snapshot"));
    }
}
