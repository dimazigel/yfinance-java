package io.ziggy.yfinance.model;

import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Company profile data from the {@code assetProfile} module. */
public record CompanyProfile(
        @Nullable String address,
        @Nullable String city,
        @Nullable String state,
        @Nullable String zip,
        @Nullable String country,
        @Nullable String phone,
        @Nullable URI website,
        @Nullable String industry,
        @Nullable String sector,
        @Nullable String longBusinessSummary,
        @Nullable Integer fullTimeEmployees,
        List<CompanyOfficer> officers) {

    public CompanyProfile {
        officers = officers == null ? List.of() : List.copyOf(officers);
    }

    /** A named company officer. */
    public record CompanyOfficer(
            @Nullable String name, @Nullable String title, @Nullable Integer age, @Nullable Long totalPay) {}
}
