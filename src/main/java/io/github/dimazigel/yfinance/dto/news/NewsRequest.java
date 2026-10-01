package io.github.dimazigel.yfinance.dto.news;

import java.util.List;

/** JSON body posted to the {@code /xhr/ncp} news stream endpoint. */
public record NewsRequest(ServiceConfig serviceConfig) {

    /**
     * The endpoint's {@code serviceConfig} object.
     *
     * @param snippetCount how many stream entries to return
     * @param s the symbols whose stream is requested
     */
    public record ServiceConfig(int snippetCount, List<String> s) {}
}
