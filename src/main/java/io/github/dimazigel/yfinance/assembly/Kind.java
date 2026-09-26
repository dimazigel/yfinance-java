package io.github.dimazigel.yfinance.assembly;

/** Whether a {@link FieldSpec} must resolve for the record to be buildable. */
public enum Kind {
    /** Must resolve from some source; absence is recorded in {@link Resolved#missingRequired()}. */
    REQUIRED,
    /** May be absent; readable through the {@code opt*} accessors on {@link Resolved}. */
    OPTIONAL,
    /** A repeated value; absence yields an empty list, never a missing-required entry. */
    LIST
}
