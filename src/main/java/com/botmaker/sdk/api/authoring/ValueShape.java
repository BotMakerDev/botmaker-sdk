package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.meta.Since;

/**
 * How many values of a {@link ValueType} a variable holds, and whether the author fixes the set they come
 * from. The axis that crosses the type catalogue rather than multiplying it.
 *
 * <p>In <em>source</em> the axis has only two positions: {@code T} and {@code List<T>}. {@link #ONE} and
 * {@link #ONE_OF} emit identically, and so do {@link #ANY_OF} and {@link #OPEN_LIST} — the difference
 * between the members of each pair is a question about the editor's widget, not about the field.
 *
 * <p>The two list shapes are separate because they were once one, and which one it meant was decided by data
 * the user could not see: tick boxes when the author had written choices down, a free-text box when they had
 * not, under one label. Splitting them makes the question the shape asks the same question the widget
 * answers.
 */
@Since("1.2.0")
public enum ValueShape {

    /** One value, free within its type. */
    ONE,
    /** One value, out of a set the author writes down. */
    ONE_OF,
    /** Several values out of that set — {@code List<T>} in source. */
    ANY_OF,
    /** A list the user fills in themselves, out of no set at all — {@code List<T>} too. */
    OPEN_LIST;

    /** Whether the author writes the set of values down. */
    public boolean hasOptions() {
        return this == ONE_OF || this == ANY_OF;
    }

    /** Whether this is emitted as {@code List<T>}. */
    public boolean isList() {
        return this == ANY_OF || this == OPEN_LIST;
    }

    /**
     * The shape {@code wire} names. Total — a shape a newer SDK invented reads as {@link #ONE}, one free
     * value, which holds the stored text rather than failing the open.
     */
    public static ValueShape fromWire(String wire) {
        if (wire == null) return ONE;
        String trimmed = wire.trim();
        for (ValueShape candidate : values()) {
            if (candidate.name().equals(trimmed)) return candidate;
        }
        return ONE;
    }
}
