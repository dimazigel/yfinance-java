package io.ziggy.yfinance.dto;

/** The {@code {code, description}} error object Yahoo embeds in its response envelopes. */
public interface YahooError {

    String code();

    String description();
}
