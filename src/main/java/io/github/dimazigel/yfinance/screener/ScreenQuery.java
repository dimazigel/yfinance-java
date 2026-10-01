package io.github.dimazigel.yfinance.screener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A screener filter over the fields {@code F}: one {@link Condition}, or several queries joined by
 * {@link Group} with AND or OR, nested as deep as needed. Build it with the factories:
 *
 * <pre>{@code
 * ScreenQuery<EquityScreenField> largeUsTech = ScreenQuery.and(
 *         ScreenQuery.eq(EquityScreenField.REGION, "us"),
 *         ScreenQuery.eq(EquityScreenField.SECTOR, "Technology"),
 *         ScreenQuery.gt(EquityScreenField.INTRADAYMARKETCAP, 100_000_000_000L));
 * }</pre>
 *
 * <p>The factories check that a comparison suits the field's {@link ScreenField.Type}; the values
 * of text fields (region codes such as {@code us}, sector names such as {@code Technology},
 * exchange codes such as {@code NMS}) are Yahoo's and are not checked here.
 *
 * @param <F> the field catalogue the query is written against, which fixes what it can screen
 */
public sealed interface ScreenQuery<F extends ScreenField> permits ScreenQuery.Condition, ScreenQuery.Group {

    /** How a {@link Condition} compares its field. */
    enum Operator {
        /** Equal to the value. */
        EQ,
        /** Greater than the value. */
        GT,
        /** Greater than or equal to the value. */
        GTE,
        /** Less than the value. */
        LT,
        /** Less than or equal to the value. */
        LTE,
        /** Between the two values. */
        BETWEEN
    }

    /** How a {@link Group} joins its queries. */
    enum Logic {
        /** Every query must match. */
        AND,
        /** At least one query must match. */
        OR
    }

    /**
     * One comparison of a field.
     *
     * @param operator the comparison
     * @param field the field compared
     * @param values what it is compared with: one {@code String} or {@code BigDecimal}, or two
     *     {@code BigDecimal}s for {@link Operator#BETWEEN}
     * @param <F> the field catalogue
     */
    record Condition<F extends ScreenField>(Operator operator, F field, List<Object> values) implements ScreenQuery<F> {

        public Condition {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(field, "field");
            values = List.copyOf(values);
        }
    }

    /**
     * Several queries joined by AND or OR.
     *
     * @param logic how the queries are joined
     * @param queries the joined queries, at least two
     * @param <F> the field catalogue
     */
    record Group<F extends ScreenField>(Logic logic, List<ScreenQuery<F>> queries) implements ScreenQuery<F> {

        public Group {
            Objects.requireNonNull(logic, "logic");
            queries = List.copyOf(queries);
            if (queries.size() < 2) {
                throw new IllegalArgumentException(logic + " needs at least two queries, got " + queries.size());
            }
        }
    }

    /** The text field equals {@code value}. */
    static <F extends ScreenField> ScreenQuery<F> eq(F field, String value) {
        require(field, ScreenField.Type.STRING, "text");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value for " + field.key() + " must not be blank");
        }
        return new Condition<>(Operator.EQ, field, List.of(value));
    }

    /** The number field equals {@code value}. */
    static <F extends ScreenField> ScreenQuery<F> eq(F field, Number value) {
        return numeric(Operator.EQ, field, value);
    }

    /** The number field is greater than {@code value}. */
    static <F extends ScreenField> ScreenQuery<F> gt(F field, Number value) {
        return numeric(Operator.GT, field, value);
    }

    /** The number field is greater than or equal to {@code value}. */
    static <F extends ScreenField> ScreenQuery<F> gte(F field, Number value) {
        return numeric(Operator.GTE, field, value);
    }

    /** The number field is less than {@code value}. */
    static <F extends ScreenField> ScreenQuery<F> lt(F field, Number value) {
        return numeric(Operator.LT, field, value);
    }

    /** The number field is less than or equal to {@code value}. */
    static <F extends ScreenField> ScreenQuery<F> lte(F field, Number value) {
        return numeric(Operator.LTE, field, value);
    }

    /** The number field lies between {@code low} and {@code high}. */
    static <F extends ScreenField> ScreenQuery<F> between(F field, Number low, Number high) {
        require(field, ScreenField.Type.NUMBER, "a number");
        BigDecimal from = decimal(field, low);
        BigDecimal to = decimal(field, high);
        if (from.compareTo(to) > 0) {
            throw new IllegalArgumentException("between(" + field.key() + "): low " + from.toPlainString()
                    + " is above high " + to.toPlainString());
        }
        return new Condition<>(Operator.BETWEEN, field, List.of(from, to));
    }

    /** The text field equals one of {@code values}: an OR of equalities, which is how Yahoo takes it. */
    @SafeVarargs
    static <F extends ScreenField> ScreenQuery<F> isIn(F field, String... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("isIn(" + field.key() + ") needs at least one value");
        }
        if (values.length == 1) {
            return eq(field, values[0]);
        }
        var equalities = new ArrayList<ScreenQuery<F>>(values.length);
        for (String value : values) {
            equalities.add(eq(field, value));
        }
        return new Group<>(Logic.OR, equalities);
    }

    /** Every one of {@code queries} matches; at least two. */
    @SafeVarargs
    static <F extends ScreenField> ScreenQuery<F> and(ScreenQuery<F>... queries) {
        return new Group<>(Logic.AND, Arrays.asList(queries));
    }

    /** At least one of {@code queries} matches; at least two. */
    @SafeVarargs
    static <F extends ScreenField> ScreenQuery<F> or(ScreenQuery<F>... queries) {
        return new Group<>(Logic.OR, Arrays.asList(queries));
    }

    private static <F extends ScreenField> ScreenQuery<F> numeric(Operator operator, F field, Number value) {
        require(field, ScreenField.Type.NUMBER, "a number");
        return new Condition<>(operator, field, List.of(decimal(field, value)));
    }

    private static void require(ScreenField field, ScreenField.Type type, String what) {
        if (field.type() != type) {
            throw new IllegalArgumentException(field.key() + " is a " + field.type() + " field and cannot be compared with " + what);
        }
    }

    /** The number as an exact decimal, so {@code 1e11} goes to Yahoo as {@code 100000000000}. */
    private static BigDecimal decimal(ScreenField field, Number value) {
        if (value instanceof BigDecimal exact) {
            return exact;
        }
        if (value instanceof Double || value instanceof Float) {
            double d = value.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("value for " + field.key() + " must be a finite number, was " + d);
            }
            return new BigDecimal(BigDecimal.valueOf(d).stripTrailingZeros().toPlainString());
        }
        return new BigDecimal(value.toString());
    }
}
