package com.botmaker.sdk.authoring;

import com.botmaker.plugin.api.ParameterGroup;
import com.botmaker.plugin.api.value.Range;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueForm;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.api.value.Visibility;

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
 * @param form        what kind of value — a catalogued leaf, or a container over other forms
 * @param value       the wire form of the current value
 * @param description a human-readable note explaining what it is for; may be empty
 * @param tag         the group it is filed under; blank means ungrouped
 * @param visibility  whether the bot's user is offered this at all
 * @param options     the declared set of values; empty for a free value, which is how having a set is asked
 * @param bounds      the declared range, for a bounded number
 * @param group       the {@link com.botmaker.plugin.api.ParameterGroup} this is filed under — which plugin
 *                    owns it, and so which generated class it becomes a field of. Blank is the default
 *                    plugin's, which is what makes every project written before groups existed read back
 *                    correctly. Unlike {@link #tag()}, this <em>is</em> a scope: names are unique within a
 *                    group, not across the project.
 */
public record VariableModel(String name, ValueForm form, List<String> value, String description,
                            String tag, Visibility visibility, List<String> options, Range bounds,
                            String group) {

    public VariableModel {
        if (name == null) name = "";
        // A stored field with no type is one older than the vocabulary, and text is the reading it has
        // always had — ValueCatalog.type(null) says the same thing. The id is all this needs: a ValueType's
        // identity *is* its id, and the label, the group and the Java type it emits belong to whichever
        // plugin registered TEXT, arriving when a catalog is merged.
        if (form == null) form = ValueForm.of(ValueType.of(ValueCatalog.TEXT_ID).build());
        value = value == null ? List.of() : List.copyOf(value);
        if (description == null) description = "";
        if (tag == null) tag = "";
        if (visibility == null) visibility = Visibility.PUBLIC;
        options = options == null ? List.of() : List.copyOf(options);
        if (bounds == null) bounds = Range.NONE;
        group = group == null ? "" : group.trim();
    }

    /** The heading a variable with no tag is listed under. Not a real tag: nothing declares it. */
    public static final String GENERAL = "General";

    /** A variable of {@code form} holding {@code value}, with nothing else declared. */
    public static VariableModel of(String name, ValueForm form, List<String> value) {
        return new VariableModel(name, form, value, "", "", Visibility.PUBLIC, List.of(), Range.NONE,
                ParameterGroup.DEFAULT_ID);
    }

    /** The single value, for the types that have exactly one; the first item of a list. */
    public String singleValue() {
        return value.isEmpty() ? "" : value.getFirst();
    }

    /** True when whoever runs the bot is offered this variable. */
    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }

    /** The tag this is filed under, or {@link #GENERAL} when it carries none. */
    public String tagOrGeneral() {
        return tag.isBlank() ? GENERAL : tag;
    }

    /** What an editor calls this — its {@link #description()} when it has one, else its {@link #name()}. */
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
        return new VariableModel(newName, form, value, description, tag, visibility, options, bounds, group);
    }

    public VariableModel withValue(List<String> newValue) {
        return new VariableModel(name, form,newValue, description, tag, visibility, options, bounds, group);
    }

    /** Convenience for the single-valued types, which is most of them. */
    public VariableModel withValue(String newValue) {
        return withValue(List.of(newValue == null ? "" : newValue));
    }

    public VariableModel withDescription(String newDescription) {
        return new VariableModel(name, form,value, newDescription, tag, visibility, options, bounds, group);
    }

    public VariableModel withTag(String newTag) {
        return new VariableModel(name, form,value, description, newTag, visibility, options, bounds, group);
    }

    public VariableModel withVisibility(Visibility newVisibility) {
        return new VariableModel(name, form,value, description, tag, newVisibility, options, bounds, group);
    }

    public VariableModel withBounds(Range newBounds) {
        return new VariableModel(name, form,value, description, tag, visibility, options, newBounds, group);
    }

    /** Files this variable under another {@link ParameterGroup} — which plugin owns it, and so which class. */
    public VariableModel withGroup(String newGroup) {
        return new VariableModel(name, form,value, description, tag, visibility, options, bounds, newGroup);
    }

    /** True when this variable belongs to {@code groupId}, reading a blank group as the default plugin's. */
    public boolean isIn(String groupId) {
        return group.equals(groupId == null ? ParameterGroup.DEFAULT_ID : groupId.trim());
    }

    // fromWire and listShapeOf stood here until 2026-09-20. They settled the one thing a stored file could
    // not state: whether an ANY_OF meant tick boxes over the author's choices or a free list the user fills
    // in, which showed only in whether any choices were written down. Both shapes emitted List<T>, and a
    // ValueForm says List<T> and nothing else — the question about the widget is asked of the row's options,
    // where it always belonged. So the reading is the canonical constructor again, and Jackson binds to it.
}
