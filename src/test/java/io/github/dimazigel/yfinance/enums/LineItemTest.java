package io.github.dimazigel.yfinance.enums;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.internal.http.YahooJsonMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class LineItemTest {

    private static final JsonMapper JSON = YahooJsonMapper.create();

    @Test
    void carriesYahooKeyAndStatement() {
        assertThat(LineItem.TOTAL_REVENUE.key()).isEqualTo("TotalRevenue");
        assertThat(LineItem.TOTAL_REVENUE.statement()).isEqualTo(StatementType.INCOME);
        assertThat(LineItem.TOTAL_ASSETS.statement()).isEqualTo(StatementType.BALANCE_SHEET);
        assertThat(LineItem.FREE_CASH_FLOW.statement()).isEqualTo(StatementType.CASH_FLOW);
    }

    @Test
    void groupsByStatement() {
        assertThat(LineItem.forStatement(StatementType.INCOME))
                .contains(LineItem.TOTAL_REVENUE, LineItem.NET_INCOME)
                .allMatch(li -> li.statement() == StatementType.INCOME);
        assertThat(LineItem.forStatement(StatementType.CASH_FLOW)).contains(LineItem.FREE_CASH_FLOW);
    }

    @Test
    void balanceSheetIncludesFinancialSectorKeysAddedUpstream() {
        // yfinance 1.6.0 (const.py): keys reported by insurers/banks.
        assertThat(LineItem.forStatement(StatementType.BALANCE_SHEET))
                .extracting(LineItem::key)
                .contains("FixedMaturityInvestments", "EquityInvestments", "NetLoan", "DeferredAssets");
    }

    @Test
    void everyKeyIsUnique() {
        assertThat(Arrays.stream(LineItem.values()).map(LineItem::key))
                .as("no two constants may carry the same wire key")
                .doesNotHaveDuplicates();
    }

    /**
     * Drift check against upstream (batch E/1): {@code src/test/resources/upstream/fundamentals_keys.json}
     * is a snapshot of Python yfinance's {@code const.py} {@code fundamentals_keys}, taken 2026-10-01.
     * Each statement's {@link LineItem} key set must be a superset of the corresponding upstream list,
     * so a future upstream addition is caught here instead of silently missing from the typed API.
     */
    @Test
    void coversEveryUpstreamFundamentalsKey() {
        Map<String, List<String>> upstream = upstreamKeys();

        assertCoversStatement(upstream, "financials", StatementType.INCOME);
        assertCoversStatement(upstream, "balance-sheet", StatementType.BALANCE_SHEET);
        assertCoversStatement(upstream, "cash-flow", StatementType.CASH_FLOW);
    }

    private static void assertCoversStatement(Map<String, List<String>> upstream, String section, StatementType type) {
        Set<String> codeKeys = LineItem.forStatement(type).stream().map(LineItem::key).collect(Collectors.toSet());
        List<String> upstreamKeys = upstream.get(section);
        assertThat(codeKeys)
                .as("%s: LineItem must carry every upstream fundamentals_keys['%s'] entry", type, section)
                .containsAll(upstreamKeys);
    }

    private static Map<String, List<String>> upstreamKeys() {
        try (var in = LineItemTest.class.getResourceAsStream("/upstream/fundamentals_keys.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: upstream/fundamentals_keys.json");
            }
            return JSON.readValue(in.readAllBytes(), new TypeReference<Map<String, List<String>>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
