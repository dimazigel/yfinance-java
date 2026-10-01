package io.github.dimazigel.yfinance.internal.service;

import io.github.dimazigel.yfinance.enums.SectorKey;
import io.github.dimazigel.yfinance.exception.YFHttpException;
import io.github.dimazigel.yfinance.exception.YFMissingDataException;
import io.github.dimazigel.yfinance.internal.api.DomainApi;
import io.github.dimazigel.yfinance.internal.dto.domain.DomainResponse;
import io.github.dimazigel.yfinance.internal.mapper.DomainMapper;
import io.github.dimazigel.yfinance.logging.LogContext;
import io.github.dimazigel.yfinance.sector.Industry;
import io.github.dimazigel.yfinance.sector.Sector;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

/** Sector and industry pages ({@code yf.Sector} / {@code yf.Industry} parity): one request each. */
public final class DomainService {

    private final DomainApi api;
    private final Clock clock;

    public DomainService(DomainApi api, Clock clock) {
        this.api = Objects.requireNonNull(api, "api");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Sector getSector(SectorKey key) {
        try (var ignored = LogContext.scope("sector")) {
            return DomainMapper.toSector(api.sector(key.wireValue()), key, clock.instant());
        }
    }

    /**
     * The industry with this key (trimmed and lower-cased; the keys come from
     * {@code Sector.industries()} and {@code EquityDetail.profile().industryKey()}).
     *
     * @throws IllegalArgumentException for a blank key
     * @throws YFMissingDataException when Yahoo has no industry with this key (it answers 404)
     */
    public Industry getIndustry(String key) {
        String normalized = key.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("industry key must not be blank");
        }
        try (var ignored = LogContext.scope("industry")) {
            DomainResponse response;
            try {
                response = api.industry(normalized);
            } catch (YFHttpException e) {
                if (e.status() == 404) {
                    throw new YFMissingDataException("industry", normalized, "Yahoo has no industry \"" + normalized + "\"");
                }
                throw e;
            }
            return DomainMapper.toIndustry(response, normalized, clock.instant());
        }
    }
}
