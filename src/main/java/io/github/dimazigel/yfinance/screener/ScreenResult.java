package io.github.dimazigel.yfinance.screener;

import io.github.dimazigel.yfinance.batch.Batch;
import io.github.dimazigel.yfinance.instrument.Instrument;
import java.util.Optional;

/**
 * One page of a screen.
 *
 * @param screen the saved screen's own description; present for a predefined screen, empty for a
 *     custom query
 * @param total how many instruments match in all
 * @param offset how many matches were skipped before this page
 * @param instruments this page, one outcome per row Yahoo returned, in its order. Each row is
 *     classified by the rules of {@code YFinance.instruments(...)}, so an outcome is the typed
 *     instrument, an {@code Unclassified} downgrade, or a skip
 */
public record ScreenResult(Optional<Info> screen, int total, int offset, Batch<Instrument> instruments) {

    /**
     * What Yahoo says about a predefined screen.
     *
     * @param id Yahoo's identifier of the saved screen
     * @param title its title, e.g. {@code Day Gainers}
     * @param description what it selects
     * @param canonicalName its canonical name, e.g. {@code DAY_GAINERS}
     */
    public record Info(String id, String title, String description, String canonicalName) {}
}
