package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.meta.Since;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A type as a variable declares it: one of the seventeen {@link ValueType}s, in one of the four
 * {@link ValueShape}s.
 *
 * <p>The shape is an axis rather than four times as many constants because it composes with all of them and
 * carries nothing of its own — {@code List<Point>} needs nothing from the catalogue that {@code Point} did
 * not already supply, beyond the box a primitive takes inside the angle brackets.
 *
 * @param type  what kind of value
 * @param shape how many, and out of what set
 */
@Since("1.2.0")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ValueChoice(ValueType type, ValueShape shape) {

    public ValueChoice {
        if (type == null) type = ValueType.TEXT;
        if (shape == null) shape = ValueShape.ONE;
        // Two shapes that a type cannot express are corrected rather than stored: a closed set has nothing
        // for an author-written subset to add, and a shape is never allowed to outlive the type it was
        // chosen for. Correcting here means every reader gets it — a file, a fixture, a caller's literal.
        if (shape == ValueShape.ONE_OF && !type.shapeable()) shape = ValueShape.ONE;
    }

    /** One free value of {@code type}. */
    public static ValueChoice of(ValueType type) {
        return new ValueChoice(type, ValueShape.ONE);
    }

    /** A list of {@code type}, filled in by whoever runs the bot. */
    public static ValueChoice listOf(ValueType type) {
        return new ValueChoice(type, ValueShape.OPEN_LIST);
    }

    /** Whether this is emitted as {@code List<T>}. */
    @JsonIgnore
    public boolean isList() {
        return shape.isList();
    }

    /** Whether the author writes down the set of values this may take. */
    @JsonIgnore
    public boolean hasOptions() {
        return shape.hasOptions();
    }

    /** How the emitter writes this: {@code java.time.Duration}, or {@code List<Key>}. */
    @JsonIgnore
    public String sourceName() {
        return isList() ? "java.util.List<" + type.boxedName() + ">" : type.sourceName();
    }

    /**
     * Reads the persisted form, including the two spellings this replaced.
     *
     * <p>A variable's type is the one part of the file whose <em>vocabulary</em> changed. Files written
     * before the shape axis existed say {@code {"type":"CHOICE","list":false}} — {@code CHOICE} being a
     * pseudo-type meaning "text out of a written-down set", and {@code list} a boolean where the shape now
     * is. Migrating here rather than in an open-time pass means every reader gets it: the project loader, a
     * hand-copied file, a test fixture.
     *
     * <p>The shape arrives as a {@code String} and not as the enum so that the parse stays <b>total</b>.
     *
     * <p>What this cannot decide is {@link ValueShape#ANY_OF} versus {@link ValueShape#OPEN_LIST} for a file
     * written before they were split: the answer is whether the variable declares choices, and the choices
     * are a sibling field this creator never sees. {@link VariableModel} settles it, where both are in hand.
     */
    @JsonCreator
    static ValueChoice fromWire(@JsonProperty("type") String type,
                                @JsonProperty("shape") String shape,
                                @JsonProperty("list") Boolean list) {
        boolean wasChoice = "CHOICE".equals(type);
        ValueType base = wasChoice ? ValueType.TEXT : ValueType.fromWire(type);
        ValueShape resolved = shape != null ? ValueShape.fromWire(shape)
                : Boolean.TRUE.equals(list) ? ValueShape.ANY_OF
                : wasChoice ? ValueShape.ONE_OF
                : ValueShape.ONE;
        return new ValueChoice(base, resolved);
    }
}
