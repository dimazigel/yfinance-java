package io.github.dimazigel.yfinance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.http.EndpointConfig;
import io.github.dimazigel.yfinance.internal.http.YahooClientFactory;
import io.github.dimazigel.yfinance.screener.EquityScreenField;
import io.github.dimazigel.yfinance.screener.FundScreenField;
import io.github.dimazigel.yfinance.screener.ScreenField;
import java.util.ArrayList;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drift detector for the screener field enums: every constant must still be in Yahoo's live field
 * catalogue with the same type, and not have become deprecated or premium. Fields Yahoo has added
 * since are not a failure here; refreshing the captured catalogue ({@code CaptureScreenerFixtures})
 * surfaces them in {@code ScreenFieldsTest}.
 */
@Tag("live")
class ScreenFieldDriftTest {

    @Test
    void equityFieldsAreStillInYahoosCatalogue() throws Exception {
        assertUsable(EquityScreenField.values(), "equity");
    }

    @Test
    void fundFieldsAreStillInYahoosCatalogue() throws Exception {
        assertUsable(FundScreenField.values(), "mutualfund");
    }

    private static void assertUsable(ScreenField[] fields, String quoteType) throws Exception {
        var client = YahooClientFactory.apiClient(EndpointConfig.production());
        var url = HttpUrl.get("https://query1.finance.yahoo.com/v1/finance/screener/instrument/" + quoteType + "/fields")
                .newBuilder().addQueryParameter("lang", "en-US").addQueryParameter("region", "US").build();
        JsonNode catalogue;
        try (var response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            catalogue = JsonMapper.builder().build().readTree(Objects.requireNonNull(response.body()).string())
                    .path("finance").path("result").path(0).path("fields");
        }
        assertThat(catalogue.size()).as("the live catalogue").isGreaterThan(fields.length);
        var problems = new ArrayList<String>();
        for (ScreenField field : fields) {
            JsonNode live = catalogue.path(field.key());
            if (live.isMissingNode()) {
                problems.add(field.key() + ": gone");
            } else if (live.path("deprecated").asBoolean(false)) {
                problems.add(field.key() + ": now deprecated");
            } else if (live.path("isPremium").asBoolean(false)) {
                problems.add(field.key() + ": now premium");
            } else if (!live.path("type").asString("").equals(field.type().name())) {
                problems.add(field.key() + ": type is now " + live.path("type").asString(""));
            }
        }
        assertThat(problems).as("screener fields that drifted from Yahoo's catalogue").isEmpty();
    }
}
