package io.github.dimazigel.yfinance.screener;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.testsupport.Fixtures;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The field enums mirror Yahoo's own catalogue (captured live from
 * {@code /v1/finance/screener/instrument/{type}/fields}): every field that is not deprecated, not
 * premium, not a per-locale ranking and not marked non-screenable, with its type. A field added to
 * or dropped from a refreshed capture fails here.
 */
class ScreenFieldsTest {

    @Test
    void equityFieldsMirrorTheCapturedCatalogue() throws Exception {
        assertThat(asMap(EquityScreenField.values())).isEqualTo(usable("screener/fields_equity.json"));
        assertThat(EquityScreenField.values()).hasSize(124);
    }

    @Test
    void fundFieldsMirrorTheCapturedCatalogue() throws Exception {
        assertThat(asMap(FundScreenField.values())).isEqualTo(usable("screener/fields_mutualfund.json"));
        assertThat(FundScreenField.values()).hasSize(27);
    }

    @Test
    void wellKnownFieldsHaveTheirKeysAndTypes() {
        assertThat(EquityScreenField.INTRADAYMARKETCAP.key()).isEqualTo("intradaymarketcap");
        assertThat(EquityScreenField.INTRADAYMARKETCAP.type()).isEqualTo(ScreenField.Type.NUMBER);
        assertThat(EquityScreenField.PERATIO_LASTTWELVEMONTHS.key()).isEqualTo("peratio.lasttwelvemonths");
        assertThat(EquityScreenField.SECTOR.type()).isEqualTo(ScreenField.Type.STRING);
        assertThat(EquityScreenField.FINANCIAL_CURRENCY.key()).isEqualTo("financialCurrency");
        assertThat(FundScreenField.FUNDNETASSETS.key()).isEqualTo("fundnetassets");
        assertThat(FundScreenField.CATEGORYNAME.type()).isEqualTo(ScreenField.Type.STRING);
    }

    private static Map<String, String> asMap(ScreenField[] fields) {
        var map = new TreeMap<String, String>();
        Arrays.stream(fields).forEach(f -> map.put(f.key(), f.type().name()));
        return map;
    }

    /** The catalogue's usable fields: key → type. BOOLEAN fields (one, an identifier flag) are left out too. */
    private static Map<String, String> usable(String fixture) throws Exception {
        JsonNode fields = JsonMapper.builder().build().readTree(Fixtures.load(fixture)).path("finance").path("result").path(0).path("fields");
        var map = new TreeMap<String, String>();
        for (JsonNode f : fields) {
            String category = f.path("category").path("categoryId").asString("");
            String type = f.path("type").asString("");
            if (f.path("deprecated").asBoolean(false) || f.path("isPremium").asBoolean(false)
                    || category.equals("ranking") || category.equals("non_screenable_field") || type.equals("BOOLEAN")) {
                continue;
            }
            map.put(f.path("fieldId").asString(), type);
        }
        return map;
    }
}
