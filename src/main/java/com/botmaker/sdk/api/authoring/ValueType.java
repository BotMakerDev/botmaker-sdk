package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.meta.Since;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.Precision;

import java.util.List;

/**
 * Every type a project variable can hold — the closed vocabulary {@code activities.json} is written in, and
 * the one the generated {@code Parameters} class is emitted from.
 *
 * <p>Seventeen constants, and they are exactly the seventeen an editor could already store. What is absent
 * is absent for one reason: it has no value anyone writes down. {@code void}; a group of templates (a
 * {@code List of Image template} says it better); and the vision <em>results</em> — a match is something the
 * bot found a moment ago, not something anyone configures.
 *
 * <h2>Why this lives in the SDK</h2>
 *
 * <p>Because the SDK emits the field. Knowing that a {@code DURATION} is written {@code java.time.Duration}
 * in source, and that a {@code KEY} inside a list is written {@code Key}, is generator knowledge; an editor
 * that kept its own copy of it would be a second author of a file with one owner. The editor keeps what is
 * genuinely editorial — the label in a menu, the group it sits in, the widget that edits it — and maps onto
 * these constants at the file boundary.
 *
 * <h2>The wire name is the constant name, and it is stable</h2>
 *
 * <p>{@link #fromWire(String)} is <b>total</b>: a type name written by a newer SDK falls back to
 * {@link #TEXT}, which holds any text and so loses nothing. Renaming a constant here rewrites every stored
 * project's meaning silently — don't.
 *
 * <h2>The source spelling, and why some of them are qualified</h2>
 *
 * <p>{@link #sourceName()} is what the emitter writes. The {@code java.time} and {@code java.awt} types are
 * written <b>fully qualified</b>, exactly as the previous generation of templates wrote them, for the reason
 * that has not changed: a generated file carries a fixed import block, and a type that needs no import
 * cannot be left out of one. The SDK's own types are written by simple name and are named here by their real
 * {@link Class} literal, so a rename in {@code api.*} breaks this file rather than a bot's build.
 */
@Since("1.2.0")
public enum ValueType {

    TEXT("String", "String", false),
    YES_NO("boolean", "Boolean", true),
    WHOLE_NUMBER("int", "Integer", true),
    DECIMAL_NUMBER("double", "Double", true),
    CHARACTER("char", "Character", true),
    COLOR("java.awt.Color", "java.awt.Color", false),

    DATE("java.time.LocalDate", "java.time.LocalDate", false),
    TIME_OF_DAY("java.time.LocalTime", "java.time.LocalTime", false),
    DURATION("java.time.Duration", "java.time.Duration", false),

    IMAGE_TEMPLATE(ImageTemplate.class),
    PRECISION(Precision.class),

    POINT(Point.class),
    RECT(Rect.class),
    SIZE(Size.class),
    DIRECTION(Direction.class),

    KEY(Key.class),
    MOUSE_BUTTON(MouseButton.class);

    private final String sourceName;
    private final String boxedName;
    private final boolean primitive;
    private final Class<?> sdkType;

    ValueType(String sourceName, String boxedName, boolean primitive) {
        this.sourceName = sourceName;
        this.boxedName = boxedName;
        this.primitive = primitive;
        this.sdkType = null;
    }

    /** An SDK type: written by its simple name, and named by the class so a rename breaks the build. */
    ValueType(Class<?> sdkType) {
        this.sourceName = sdkType.getSimpleName();
        this.boxedName = sdkType.getSimpleName();
        this.primitive = false;
        this.sdkType = sdkType;
    }

    /** The stable wire form, as {@code activities.json} spells it — the constant's own name. */
    public String wireName() {
        return name();
    }

    /** How the emitter writes this type in source: {@code int}, {@code Key}, {@code java.time.Duration}. */
    public String sourceName() {
        return sourceName;
    }

    /** How it is written inside {@code List<…>} — {@code Integer} for {@code int}. */
    public String boxedName() {
        return boxedName;
    }

    public boolean isPrimitive() {
        return primitive;
    }

    /** The {@code api.*} class this names, or null for a primitive or a JDK type. */
    public Class<?> sdkType() {
        return sdkType;
    }

    /**
     * Whether this type's values <em>are</em> a set the editor already shows in full — which is what makes
     * {@link ValueShape#ONE_OF} over it meaningless. "One of yes and no" is a boolean, said twice and worse.
     */
    public boolean isClosedSet() {
        return switch (this) {
            case YES_NO, DIRECTION, KEY, MOUSE_BUTTON -> true;
            default -> false;
        };
    }

    /** Whether an author can usefully write down a set of values of this type. */
    public boolean shapeable() {
        return !isClosedSet();
    }

    /** Every storable type, in declaration order. */
    public static List<ValueType> all() {
        return List.of(values());
    }

    /**
     * The type {@code wire} names. Total — an unrecognised name reads as {@link #TEXT}, which holds anything
     * and so loses no stored value, rather than failing the project open.
     *
     * <p>{@code CHOICE} is recognised as a legacy spelling: before the shape axis existed, a variable whose
     * values came from a written-down set was typed {@code CHOICE} with no element type at all. It was text
     * then and it is {@link #TEXT} now; what it lost — the shape — is restored by
     * {@link ValueChoice#fromWire}.
     */
    public static ValueType fromWire(String wire) {
        if (wire == null) return TEXT;
        String trimmed = wire.trim();
        for (ValueType candidate : values()) {
            if (candidate.name().equals(trimmed)) return candidate;
        }
        return TEXT;
    }
}
