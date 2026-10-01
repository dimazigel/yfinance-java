package io.github.dimazigel.yfinance.internal.mapper;

import io.github.dimazigel.yfinance.exception.YFDataException;
import io.github.dimazigel.yfinance.fundamentals.SharesOutstanding;
import io.github.dimazigel.yfinance.internal.dto.timeseries.SharesResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps the raw shares-outstanding timeseries response into {@link SharesOutstanding} points. */
public final class SharesMapper {

    private static final Logger LOG = LoggerFactory.getLogger(SharesMapper.class);

    private SharesMapper() {}

    /**
     * Pairs {@code timestamp[i]} with {@code shares_out[i]}, dropping a point whose timestamp or
     * value is null (DEBUG). A missing {@code result}/{@code shares_out} is an empty list — Yahoo
     * answers that for non-equities and symbols with no reported history, not an error. If the two
     * arrays differ in length, the mismatch is logged once at DEBUG (both lengths) and only the
     * overlapping prefix is mapped.
     */
    public static List<SharesOutstanding> toList(SharesResponse response) {
        var ts = response.timeseries();
        if (ts == null) {
            throw new YFDataException("Malformed timeseries response");
        }
        if (ts.error() != null) {
            throw new YFDataException("Yahoo timeseries error: " + MapperSupport.describe(ts.error()));
        }
        var results = ts.result();
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        var result = results.getFirst();
        List<@Nullable Long> timestamps = result.timestamp();
        List<@Nullable Long> sharesOut = result.sharesOut();
        if (timestamps == null || sharesOut == null) {
            return List.of();
        }

        if (timestamps.size() != sharesOut.size()) {
            LOG.atDebug().addKeyValue("timestampCount", timestamps.size()).addKeyValue("sharesOutCount", sharesOut.size())
                    .log("shares-outstanding timestamp/shares_out length mismatch ({} vs {}); mapping the overlapping prefix only",
                            timestamps.size(), sharesOut.size());
        }

        var points = new ArrayList<SharesOutstanding>();
        int skipped = 0;
        int n = Math.min(timestamps.size(), sharesOut.size());
        for (int i = 0; i < n; i++) {
            Long epochSeconds = timestamps.get(i);
            Long shares = sharesOut.get(i);
            if (epochSeconds == null || shares == null) {
                skipped++;
                continue;
            }
            LocalDate date = LocalDate.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
            points.add(new SharesOutstanding(date, shares));
        }
        if (skipped > 0) {
            LOG.atDebug().addKeyValue("skipped", skipped)
                    .log("Skipped {} shares-outstanding {} without a usable timestamp or value",
                            skipped, skipped == 1 ? "point" : "points");
        }
        return List.copyOf(points);
    }
}
