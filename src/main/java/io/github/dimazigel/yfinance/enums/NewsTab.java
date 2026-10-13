package io.github.dimazigel.yfinance.enums;

/**
 * Tab of a symbol's news stream on Yahoo Finance; the wire value is the list slug the news gateway
 * is asked for (the {@code listInput.slug} variable of the {@code TickerNews} query).
 */
public enum NewsTab implements WireEnum {
    /** Everything: news, videos and press releases. */
    ALL("list=finance-US-en-US-ticker-all"),
    /** Editorial news and videos. */
    NEWS("list=finance-US-en-US-ticker-news"),
    /** Press releases from the newswires. */
    PRESS_RELEASES("list=finance-US-en-US-ticker-pressrelease");

    private final String wireValue;

    NewsTab(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }
}
