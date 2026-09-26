package io.github.dimazigel.yfinance.model;

import io.github.dimazigel.yfinance.exception.YFMissingDataException;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Turns a {@code @Nullable} model value into a non-null one or a clear failure. Most model fields
 * are nullable because Yahoo omits them per instrument (an index has no {@code marketCap}, crypto
 * has no EPS); a caller that <em>knows</em> it is looking at an equity can insist:
 *
 * <pre>{@code
 * BigDecimal cap = quote.require(q -> q.price().marketCap(), "marketCap");   // symbol-aware sugar
 * BigDecimal pct = Required.value(holder, Holders.InstitutionalHolder::pctHeld, "pctHeld");
 * }</pre>
 *
 * <p>The failure is a {@link YFMissingDataException} naming the field and the subject, instead of a
 * {@code NullPointerException} some lines later.
 */
public final class Required {

    private Required() {}

    /**
     * {@code accessor.apply(source)}, or a {@link YFMissingDataException} if that is {@code null}.
     * The subject in the message is the source's simple class name; records that know their symbol
     * offer {@code require(...)} overloads that name it instead.
     */
    public static <T, V> V value(T source, Function<? super T, @Nullable V> accessor, String field) {
        return value(source, accessor, field, source.getClass().getSimpleName(), false);
    }

    static <T, V> V value(T source, Function<? super T, @Nullable V> accessor, String field, Object subject) {
        return value(source, accessor, field, String.valueOf(subject), true);
    }

    private static <T, V> V value(
            T source, Function<? super T, @Nullable V> accessor, String field, String subject, boolean named) {
        V value = accessor.apply(source);
        if (value == null) {
            String message = named
                    ? field + " is not available for " + subject
                    : field + " is not available (" + subject + ")";
            throw new YFMissingDataException(field, subject, message);
        }
        return value;
    }
}
