package io.github.dimazigel.yfinance.internal.dto.news;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.dimazigel.yfinance.enums.NewsTab;
import java.util.List;

/**
 * JSON body posted to Yahoo's news gateway ({@code https://nexus-gateway-prod.media.yahoo.com/}): a
 * GraphQL request whose {@link #QUERY} is a trimmed copy of the {@code FinancePolarisTickerListNews}
 * query the finance.yahoo.com quote page sends (captured 2026-10-13), keeping only the fields
 * {@code NewsMapper} reads. The variables are the page's own except {@code first}, the item count.
 *
 * <p>The gateway validates the document against its schema, so a field it does not know is a
 * 400 with {@code GRAPHQL_VALIDATION_FAILED}; the listing flags and the four client-context fields
 * are all required ({@code BAD_USER_INPUT} / {@code VALIDATION_INVALID_TYPE_VARIABLE} otherwise),
 * and the ranking weights decide the order and the size of the stream, so they are the page's.
 */
public record NewsRequest(String operationName, String query, Variables variables) {

    /** The operation name of {@link #QUERY}. */
    public static final String OPERATION = "TickerNews";

    /** The GraphQL document; {@code mlRecsStream(first:)} is what bounds the stream. */
    public static final String QUERY = """
            query TickerNews($listInput: LightyearListInput!, $clientContext: ClientContext!, $mlRecsInput: MLRecsInput!, $first: Int) {
              lightyearList: financeTickerList(list_input: $listInput, cc: $clientContext) {
                main: mlRecsStream(mlRecsInput: $mlRecsInput, first: $first) {
                  edges { node { asset { __typename ... on Story { ...StoryFields } ... on Video { ...VideoFields } } } }
                  totalCount
                }
              }
            }
            fragment Attributes on ContentAttributes {
              summary
              pubDate: publishTime
              canonicalUrl
              clickthroughUrl(cc: $clientContext)
              provider { displayName url }
              thumbnail { originalUrl: url originalHeight: height originalWidth: width }
            }
            fragment StoryFields on Story { id: uuid title isPremiumNews contentAttributes { ...Attributes } }
            fragment VideoFields on Video { id: uuid title contentAttributes { ...Attributes } }
            """;

    private static final ClientContext CLIENT_CONTEXT = new ClientContext("desktop", "en-US", "US", "finance");

    /** The request for up to {@code count} items of {@code symbol}'s stream on {@code tab}. */
    public static NewsRequest tickerNews(NewsTab tab, String symbol, int count) {
        List<String> symbols = List.of(symbol);
        return new NewsRequest(OPERATION, QUERY, new Variables(
                CLIENT_CONTEXT,
                new ListInput(false, false, true, false, true, new QueryVariables(symbols), tab.wireValue()),
                new MlRecsInput("FINANCE", new RankInput(new FinanceQuoteNewsV2(0.05, 16, 24, 1, symbols))),
                count));
    }

    /** The query's variables. */
    public record Variables(ClientContext clientContext, ListInput listInput, MlRecsInput mlRecsInput, int first) {}

    /** Who is asking; every field is required by the schema. */
    public record ClientContext(String device, String lang, String region, String site) {}

    /** Which list to read: the tab's slug, for the symbol. */
    public record ListInput(
            boolean disableDedupe,
            boolean enableBlockedContent,
            boolean enableQueryTimeLicenseCheck,
            boolean filterClientContext,
            @JsonProperty("getFullList") boolean getFullList,
            QueryVariables queryVariables,
            String slug) {}

    /** The list's symbol filter. */
    public record QueryVariables(List<String> tickerSymbol) {}

    /** How the stream is ranked. */
    public record MlRecsInput(String instance, RankInput rankInput) {}

    /** The ranking model. */
    public record RankInput(FinanceQuoteNewsV2 financeQuoteNewsV2) {}

    /** The quote page's ranking weights: recency first, a little aboutness, a 24 h half-life. */
    public record FinanceQuoteNewsV2(
            double aboutnessWeight, int boostDurationHours, int halfLifeHours, double recencyWeight, List<String> tickerSymbol) {}
}
