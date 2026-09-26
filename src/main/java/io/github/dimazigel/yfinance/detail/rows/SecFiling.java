package io.github.dimazigel.yfinance.detail.rows;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/** An SEC filing reference. */
public record SecFiling(Optional<Instant> date, Optional<String> type, String title, Optional<URI> url) {}
