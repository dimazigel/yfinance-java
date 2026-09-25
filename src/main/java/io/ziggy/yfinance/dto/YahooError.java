package io.ziggy.yfinance.dto;

import org.jspecify.annotations.Nullable;

/** The {@code {code, description}} error object Yahoo embeds in its response envelopes. */
public interface YahooError {

    @Nullable String code();

    @Nullable String description();
}
