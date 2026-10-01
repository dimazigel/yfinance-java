package io.github.dimazigel.yfinance.enums;

/** Tab of a symbol's news stream on Yahoo Finance; the wire value is the news endpoint's {@code queryRef}. */
public enum NewsTab implements WireEnum {
    /** Everything: news, videos and press releases. */
    ALL("newsAll"),
    /** Editorial news and videos. */
    NEWS("latestNews"),
    /** Press releases from the newswires. */
    PRESS_RELEASES("pressRelease");

    private final String wireValue;

    NewsTab(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }
}
