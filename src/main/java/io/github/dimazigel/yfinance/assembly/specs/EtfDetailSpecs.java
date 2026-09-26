package io.github.dimazigel.yfinance.assembly.specs;

import static io.github.dimazigel.yfinance.assembly.FieldSpec.optional;
import static io.github.dimazigel.yfinance.assembly.FieldSpec.required;
import static io.github.dimazigel.yfinance.assembly.Unit.RAW;

import io.github.dimazigel.yfinance.assembly.FieldSpec;
import java.util.List;

/** ETF detail fields. */
public final class EtfDetailSpecs {

    private EtfDetailSpecs() {}

    private static final List<FieldSpec> OWN = List.of(
            required("legalType", RAW, "qs:fundProfile.legalType", "qs:defaultKeyStatistics.legalType"),
            optional("beta3Year", RAW, "qs:defaultKeyStatistics.beta3Year"),
            optional("longBusinessSummary", RAW, "qs:assetProfile.longBusinessSummary"),
            optional("styleBoxUrl", RAW, "qs:fundProfile.styleBoxUrl"));

    public static final List<FieldSpec> DETAIL = Specs.concat(FundDetailSpecs.COMMON, OWN);
}
