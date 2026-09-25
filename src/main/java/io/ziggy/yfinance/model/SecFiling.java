package io.ziggy.yfinance.model;

import java.net.URI;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** An SEC filing reference. */
public record SecFiling(@Nullable Instant date, @Nullable String type, @Nullable String title, @Nullable URI url) {}
