package io.github.dimazigel.yfinance.screener;

import static io.github.dimazigel.yfinance.screener.EquityScreenField.INTRADAYMARKETCAP;
import static io.github.dimazigel.yfinance.screener.EquityScreenField.PERATIO_LASTTWELVEMONTHS;
import static io.github.dimazigel.yfinance.screener.EquityScreenField.REGION;
import static io.github.dimazigel.yfinance.screener.EquityScreenField.SECTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.dimazigel.yfinance.screener.ScreenQuery.Condition;
import io.github.dimazigel.yfinance.screener.ScreenQuery.Group;
import io.github.dimazigel.yfinance.screener.ScreenQuery.Logic;
import io.github.dimazigel.yfinance.screener.ScreenQuery.Operator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenQueryTest {

    @Test
    void factoriesBuildConditionsWithExactNumbers() {
        assertThat(ScreenQuery.eq(REGION, "us")).isEqualTo(new Condition<>(Operator.EQ, REGION, List.of("us")));
        assertThat(ScreenQuery.gt(INTRADAYMARKETCAP, 100_000_000_000L))
                .isEqualTo(new Condition<>(Operator.GT, INTRADAYMARKETCAP, List.of(new BigDecimal("100000000000"))));
        assertThat(ScreenQuery.lte(PERATIO_LASTTWELVEMONTHS, 12.5))
                .isEqualTo(new Condition<>(Operator.LTE, PERATIO_LASTTWELVEMONTHS, List.of(new BigDecimal("12.5"))));
        assertThat(ScreenQuery.between(PERATIO_LASTTWELVEMONTHS, 5, 20)).isEqualTo(
                new Condition<>(Operator.BETWEEN, PERATIO_LASTTWELVEMONTHS, List.of(new BigDecimal("5"), new BigDecimal("20"))));
        assertThat(ScreenQuery.eq(PERATIO_LASTTWELVEMONTHS, 10))
                .isEqualTo(new Condition<>(Operator.EQ, PERATIO_LASTTWELVEMONTHS, List.of(new BigDecimal("10"))));
    }

    @Test
    void andAndOrGroupTheirOperandsInOrder() {
        var us = ScreenQuery.eq(REGION, "us");
        var large = ScreenQuery.gte(INTRADAYMARKETCAP, 1e10);

        ScreenQuery<EquityScreenField> both = ScreenQuery.and(us, large);

        assertThat(both).isEqualTo(new Group<>(Logic.AND, List.of(us, large)));
        assertThat(ScreenQuery.or(us, large)).isEqualTo(new Group<>(Logic.OR, List.of(us, large)));
        assertThat(ScreenQuery.and(both, ScreenQuery.eq(SECTOR, "Technology"))).isInstanceOfSatisfying(Group.class,
                g -> assertThat(g.queries()).hasSize(2).first().isEqualTo(both));
    }

    @Test
    void isInIsAnOrOfEqualities() {
        assertThat(ScreenQuery.isIn(SECTOR, "Technology", "Healthcare")).isEqualTo(new Group<>(Logic.OR,
                List.of(ScreenQuery.eq(SECTOR, "Technology"), ScreenQuery.eq(SECTOR, "Healthcare"))));
        assertThat(ScreenQuery.isIn(SECTOR, "Technology")).as("one value needs no group").isEqualTo(ScreenQuery.eq(SECTOR, "Technology"));
    }

    @Test
    void comparingATextFieldNumericallyOrANumberFieldTextuallyIsRejected() {
        assertThatThrownBy(() -> ScreenQuery.gt(SECTOR, 1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sector");
        assertThatThrownBy(() -> ScreenQuery.between(REGION, 1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenQuery.eq(SECTOR, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenQuery.eq(INTRADAYMARKETCAP, "big")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intradaymarketcap");
    }

    @Test
    void malformedQueriesAreRejected() {
        var us = ScreenQuery.eq(REGION, "us");
        assertThatThrownBy(() -> ScreenQuery.and(us)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least two");
        assertThatThrownBy(() -> ScreenQuery.<EquityScreenField>or()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenQuery.isIn(SECTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenQuery.between(PERATIO_LASTTWELVEMONTHS, 20, 5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20");
        assertThatThrownBy(() -> ScreenQuery.eq(REGION, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenQuery.gt(INTRADAYMARKETCAP, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queryListsAreImmutableCopies() {
        var operands = new java.util.ArrayList<ScreenQuery<EquityScreenField>>(
                List.of(ScreenQuery.eq(REGION, "us"), ScreenQuery.eq(SECTOR, "Technology")));
        var group = new Group<>(Logic.AND, operands);
        operands.clear();

        assertThat(group.queries()).hasSize(2);
        assertThatThrownBy(() -> group.queries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void optionsValidateTheirWindowAndCarryASort() {
        ScreenOptions defaults = ScreenOptions.defaults();
        assertThat(defaults.offset()).isZero();
        assertThat(defaults.size()).isEqualTo(25);
        assertThat(defaults.sort()).isEmpty();

        ScreenOptions page = defaults.withOffset(50).withSize(250).sortedBy(INTRADAYMARKETCAP, false);
        assertThat(page.offset()).isEqualTo(50);
        assertThat(page.size()).isEqualTo(250);
        assertThat(page.sort()).contains(new ScreenOptions.Sort(INTRADAYMARKETCAP, false));

        assertThatThrownBy(() -> defaults.withSize(251)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("250");
        assertThatThrownBy(() -> defaults.withSize(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaults.withOffset(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
