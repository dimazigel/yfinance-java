package io.github.dimazigel.yfinance.internal.dto.screener;

import java.util.Map;

/**
 * JSON body posted to {@code /v1/finance/screener} for a custom query.
 *
 * @param offset matches to skip
 * @param size rows to return
 * @param sortField id of the field to sort by
 * @param sortType {@code ASC} or {@code DESC}
 * @param quoteType {@code EQUITY} or {@code MUTUALFUND}
 * @param userId empty: the query is anonymous
 * @param userIdType always {@code guid}
 * @param query the operator tree, {@code {operator, operands}} nested
 */
public record ScreenerRequest(
        int offset,
        int size,
        String sortField,
        String sortType,
        String quoteType,
        String userId,
        String userIdType,
        Map<String, Object> query) {}
