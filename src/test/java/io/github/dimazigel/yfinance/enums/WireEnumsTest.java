package io.github.dimazigel.yfinance.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WireEnumsTest {

    @Test
    void intervalWireValuesRoundTrip() {
        assertThat(Interval.ONE_DAY.wireValue()).isEqualTo("1d");
        assertThat(Interval.ONE_MONTH.wireValue()).isEqualTo("1mo");
        assertThat(Interval.fromWire("1wk")).isEqualTo(Interval.ONE_WEEK);
        assertThat(Interval.fromWire("90m")).isEqualTo(Interval.NINETY_MINUTES);
    }

    @Test
    void rangeWireValuesRoundTrip() {
        assertThat(Range.MAX.wireValue()).isEqualTo("max");
        assertThat(Range.YEAR_TO_DATE.wireValue()).isEqualTo("ytd");
        assertThat(Range.fromWire("6mo")).isEqualTo(Range.SIX_MONTHS);
    }

    @Test
    void eventTypeWireValues() {
        assertThat(EventType.DIVIDENDS.wireValue()).isEqualTo("div");
        assertThat(EventType.CAPITAL_GAINS.wireValue()).isEqualTo("capitalGains");
    }

    @Test
    void lookupTypeWireValuesAreLowercase() {
        assertThat(LookupType.CRYPTOCURRENCY.wireValue()).isEqualTo("cryptocurrency");
        assertThat(LookupType.MUTUAL_FUND.wireValue()).isEqualTo("mutualfund");
        assertThat(LookupType.ALL.wireValue()).isEqualTo("all");
    }

    @Test
    void fromWireRejectsUnknown() {
        assertThatThrownBy(() -> Interval.fromWire("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionTypeHasCallAndPut() {
        assertThat(OptionType.values()).containsExactly(OptionType.CALL, OptionType.PUT);
    }
}
