package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.meta.Since;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One project variable: a named, typed value, and the field the generated {@code Parameters} class carries
 * for it.
 *
 * <p><b>Every variable belongs to the project, not to an activity.</b> A delay two activities both wait for
 * is one variable they both read, rather than a copy each. {@link #tag()} is what organises them for a
 * reader — a <em>view</em>, never a scope.
 *
 * <p><b>The value is text.</b> {@link #value()} is a list of strings: one entry for an ordinary variable, one
 * per item for a list-shaped one. One shape on disk means one reader and one writer, which is worth the
 * {@code ["90s"]} a duration reads as in the file. Turning that text into the Java literal the field is
 * initialised with is the emitter's job, and the emitter is the only thing that has to know how.
 *
 * <p><b>Plain data.</b> Unlike the editor's own variable record, nothing here normalises, clamps or
 * re-derives — a model handed to {@link Authoring} is written as given, and a model read back is what the
 * file said. Whatever coercion an editor wants belongs in the editor, where the user can see it happen.
 *
 * @param name        the generated field name; a valid Java identifier
 * @param type        what kind of value, and in what shape
 * @param value       the wire form of the current value
 * @param description a human-readable note explaining what it is for; may be empty
 * @param tag         the group it is filed under; blank means ungrouped
 * @param visibility  whether the bot's user is offered this at all
 * @param options     the declared set of values, for a shape that {@link ValueChoice#hasOptions has one}
 * @param bounds      the declared range, for a bounded number
 */
@Since("1.2.0")
@JsonIgnoreProperties(ignoreUnknown = true)
public record VariableModel(String name, ValueChoice type, List<String> value, String description,
                            String tag, Visibility visibility, List<String> options, Range bounds) {

    public VariableModel {
        if (name == null) name = "";
        if (type == null) type = ValueChoice.of(ValueType.TEXT);
        value = value == null ? List.of() : List.copyOf(value);
        if (description == null) description = "";
        if (tag == null) tag = "";
        if (visibility == null) visibility = Visibility.PUBLIC;
        options = options == null ? List.of() : List.copyOf(options);
        if (bounds == null) bounds = Range.NONE;
    }

    /** The heading a variable with no tag is listed under. Not a real tag: nothing declares it. */
    public static final String GENERAL = "General";

    /** A variable of {@code type} holding {@code value}, with nothing else declared. */
    public static VariableModel of(String name, ValueChoice type, List<String> value) {
        return new VariableModel(name, type, value, "", "", Visibility.PUBLIC, List.of(), Range.NONE);
    }

    /** The single value, for the types that have exactly one; the first item of a list. */
    @JsonIgnore
    public String singleValue() {
        return value.isEmpty() ? "" : value.getFirst();
    }

    /** True when whoever runs the bot is offered this variable. */
    @JsonIgnore
    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }

    /** The tag this is filed under, or {@link #GENERAL} when it carries none. */
    @JsonIgnore
    public String tagOrGeneral() {
        return tag.isBlank() ? GENERAL : tag;
    }

    /** What an editor calls this — its {@link #description()} when it has one, else its {@link #name()}. */
    @JsonIgnore
    public String displayLabel() {
        return description.isBlank() ? name : description;
    }

    // ---- copies -----------------------------------------------------------------------------------------
    //
    // Plain copies, in keeping with the record: each swaps one component and re-derives nothing. The two
    // edits that cannot be plain — retyping (whose value must be reset to the new type's default) and
    // replacing the declared options (whose value must be pruned to what is still on offer) — are not here,
    // because both need the coercion rules, and those belong to the editor rather than to the file.

    public VariableModel withName(String newName) {
        return new VariableModel(newName, type, value, description, tag, visibility, options, bounds);
    }

    public VariableModel withValue(List<String> newValue) {
        return new VariableModel(name, type, newValue, description, tag, visibility, options, bounds);
    }

    /** Convenience for the single-valued types, which is most of them. */
    public VariableModel withValue(String newValue) {
        return withValue(List.of(newValue == null ? "" : newValue));
    }

    public VariableModel withDescription(String newDescription) {
        return new VariableModel(name, type, value, newDescription, tag, visibility, options, bounds);
    }

    public VariableModel withTag(String newTag) {
        return new VariableModel(name, type, value, description, newTag, visibility, options, bounds);
    }

    public VariableModel withVisibility(Visibility newVisibility) {
        return new VariableModel(name, type, value, description, tag, newVisibility, options, bounds);
    }

    public VariableModel withBounds(Range newBounds) {
        return new VariableModel(name, type, value, description, tag, visibility, options, newBounds);
    }

    /**
     * Reads the persisted form, settling the one question {@link ValueChoice#fromWire} cannot.
     *
     * <p>{@link ValueShape#ANY_OF} once meant two things — tick boxes over the author's choices, or a free
     * list the user filled in — and which one it was showed only in whether any choices were written down.
     * Now that they are two shapes, a file written before the split has to be read the way it used to
     * <em>render</em>, or a project full of "List of text" parameters opens as tick boxes over nothing.
     *
     * <p>So: a stored {@code ANY_OF} keeps its shape when there is a set behind it — the author's options, or
     * the type's own constants for a closed set like {@code Direction} — and becomes
     * {@link ValueShape#OPEN_LIST} when there is not.
     */
    @JsonCreator
    static VariableModel fromWire(@JsonProperty("name") String name,
                                  @JsonProperty("type") ValueChoice type,
                                  @JsonProperty("value") List<String> value,
                                  @JsonProperty("description") String description,
                                  @JsonProperty("tag") String tag,
                                  @JsonProperty("visibility") Visibility visibility,
                                  @JsonProperty("options") List<String> options,
                                  @JsonProperty("bounds") Range bounds) {
        return new VariableModel(name, listShapeOf(type, options), value, description, tag, visibility,
                options, bounds);
    }

    /** {@link #fromWire}'s rule, alone so it can be read — and tested — without a file. */
    static ValueChoice listShapeOf(ValueChoice type, List<String> options) {
        if (type == null || type.shape() != ValueShape.ANY_OF) return type;
        boolean hasSet = (options != null && !options.isEmpty()) || type.type().isClosedSet();
        return hasSet ? type : new ValueChoice(type.type(), ValueShape.OPEN_LIST);
    }
}
