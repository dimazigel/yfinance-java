/**
 * A typed Java client for Yahoo Finance.
 *
 * <p>Only the exported packages are API. Everything under
 * {@code io.github.dimazigel.yfinance.internal} — the Feign bindings, wire DTOs, mappers, the
 * assembly engine, the services, the auth handshake and the HTTP plumbing — is encapsulated and
 * may change without notice.
 */
module io.github.dimazigel.yfinance {
    requires transitive okhttp3;
    requires transitive org.jspecify;
    requires transitive org.slf4j;

    requires com.fasterxml.jackson.annotation;
    requires feign.core;
    requires feign.jackson3;
    requires feign.okhttp;
    requires tools.jackson.databind;

    exports io.github.dimazigel.yfinance;
    exports io.github.dimazigel.yfinance.batch;
    exports io.github.dimazigel.yfinance.detail;
    exports io.github.dimazigel.yfinance.detail.rows;
    exports io.github.dimazigel.yfinance.enums;
    exports io.github.dimazigel.yfinance.exception;
    exports io.github.dimazigel.yfinance.fundamentals;
    exports io.github.dimazigel.yfinance.http;
    exports io.github.dimazigel.yfinance.instrument;
    exports io.github.dimazigel.yfinance.logging;
    exports io.github.dimazigel.yfinance.market;
    exports io.github.dimazigel.yfinance.news;
    exports io.github.dimazigel.yfinance.search;
    exports io.github.dimazigel.yfinance.sector;
    exports io.github.dimazigel.yfinance.valueobject;

    // Jackson builds the wire records reflectively.
    opens io.github.dimazigel.yfinance.internal.dto to tools.jackson.databind;
    opens io.github.dimazigel.yfinance.internal.dto.chart to tools.jackson.databind;
    opens io.github.dimazigel.yfinance.internal.dto.domain to tools.jackson.databind;
    opens io.github.dimazigel.yfinance.internal.dto.lookup to tools.jackson.databind;
    opens io.github.dimazigel.yfinance.internal.dto.news to tools.jackson.databind;
    opens io.github.dimazigel.yfinance.internal.dto.options to tools.jackson.databind;
    opens io.github.dimazigel.yfinance.internal.dto.search to tools.jackson.databind;
    opens io.github.dimazigel.yfinance.internal.dto.timeseries to tools.jackson.databind;
}
