package com.botmaker.sdk.internal.plugin;

import com.botmaker.plugin.api.ParameterEdit;
import com.botmaker.plugin.api.ParameterRow;
import com.botmaker.plugin.api.value.Range;
import com.botmaker.plugin.api.value.ValueChoice;
import com.botmaker.plugin.api.value.Visibility;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.ProjectModel;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.sdk.authoring.VariableModel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This plugin's side of the Parameters window: the rows it owns, and where a changed value goes.
 *
 * <p><b>The point of this class is that the data stopped being the host's.</b> Studio parsed
 * {@code activities.json} itself, drew the rows out of its own records and wrote the file back — which meant
 * the host knew one plugin's storage format, and no second plugin could have had parameters at all. The
 * contract's parameter-data surface (2026-09-10) cut that: the host keeps the window, the ordering, the
 * rendering and the undo; the owner keeps the file, the meaning of the text and when it is written. This is
 * the owner's half for the group the SDK plugin declares.
 *
 * <h2>The version is this jar's, and that is not a shortcut</h2>
 *
 * <p>Every {@link Authoring} method takes an {@link SdkVersion} because <em>a bot gets its answers from its
 * own SDK version</em>. Here the answer is {@link SdkVersion#latest()} rather than the project's pin, and it
 * is right for a reason worth stating once: this plugin is loaded off <b>the open project's own resolved
 * classpath</b>, so the jar running this code <em>is</em> the SDK that project pins. Reading its pom for a
 * version and then honouring it would be this jar pretending to be a different one.
 *
 * <h2>This is the editor now, so this is where the coercion lives</h2>
 *
 * <p>Written on 2026-09-10, this class said <em>nothing here coerces</em>, on the standing rule that
 * clamping, pruning and resetting a retyped value belong to the editor rather than to the file — which is why
 * {@link VariableModel} is plain data and normalises nothing. Later the same day the maintainer moved the
 * Parameters editor itself into this plugin, so the editor is here: {@link SdkValues} carries the rules
 * unchanged from Studio's {@code ValueWire}, and every verb below runs them. The rule did not change; the
 * editor did.
 *
 * <p>So {@link #apply} answers the row it stored and the row it stored may differ from the edit — a number
 * pulled to its {@link com.botmaker.plugin.api.value.Range}, a duration spelled canonically, a choice that
 * is no longer on offer replaced by one that is. That is exactly what the surface's return type is for.
 *
 * <h2>The declaration verbs are not on the contract, deliberately</h2>
 *
 * <p>Declaring, removing, renaming and retyping a parameter never cross a plugin boundary: the window that
 * does them is this plugin's own. The contract keeps the two verbs a <em>host's</em> window needs — read the
 * rows, take a changed value — and never learns what retyping means.
 */
public final class SdkParameters {

    private final Path resourcesDir;
    private final String groupId;

    /**
     * @param resourcesDir the open project's resources directory, from
     *                     {@code StudioServices.resourcesDir()}
     * @param groupId      the {@code ParameterGroup} id this serves; every other id answers nothing
     */
    public SdkParameters(Path resourcesDir, String groupId) {
        this.resourcesDir = resourcesDir;
        this.groupId = groupId == null ? "" : groupId.trim();
    }

    /**
     * The rows filed under {@code requested}, in the order the file holds them.
     *
     * <p>Answers nothing for an id this plugin does not own, and nothing for a project whose file cannot be
     * read — an unreadable or absent {@code activities.json} is a project with no parameters yet, which is
     * exactly what a new project is, and not a reason to refuse to draw the window.
     */
    public List<ParameterRow> rows(String requested) {
        if (!groupId.equals(requested == null ? "" : requested.trim())) return List.of();
        ProjectModel model = read();
        if (model == null) return List.of();
        List<ParameterRow> rows = new ArrayList<>();
        for (VariableModel variable : model.variables()) {
            if (variable.isIn(groupId)) rows.add(rowOf(variable));
        }
        return List.copyOf(rows);
    }

    /**
     * Stores {@code edit} and answers the row as stored, or empty when this plugin owns neither the group nor
     * a row of that name.
     *
     * <p>Empty is <em>not mine</em>, and it is deliberately not how a failed save reports itself: the host
     * reads empty as "leave the screen alone", so a write that could not happen throws instead — contained
     * and reported by the host, with the row left as it was, which is the truth rather than a silent
     * discard.
     */
    public Optional<ParameterRow> apply(ParameterEdit edit) {
        if (edit == null || !groupId.equals(edit.groupId())) return Optional.empty();
        ProjectModel model = read();
        if (model == null) return Optional.empty();

        List<VariableModel> variables = new ArrayList<>(model.variables());
        int at = indexOf(variables, edit.name());
        if (at < 0) return Optional.empty();

        VariableModel held = variables.get(at);
        VariableModel stored = held.withValue(
                SdkValues.normalize(edit.value(), held.type(), held.options(), held.bounds()));
        variables.set(at, stored);
        write(model.withVariables(variables));
        return Optional.of(rowOf(stored));
    }

    // ---- the declaration verbs, which are this plugin's own window's and nobody else's -------------------

    /**
     * Declares a new parameter of {@code type}, seeded with that type's default value.
     *
     * <p>Empty when the name is blank or already taken <em>in this group</em>. A name is unique within a
     * group and only there, which is what lets two plugins both offer a {@code timeout}; it is also a
     * generated field name, so a name that is not a Java identifier is refused rather than stored and
     * discovered at the next build.
     */
    public Optional<ParameterRow> declare(String name, ValueChoice type) {
        String wanted = name == null ? "" : name.trim();
        if (!isIdentifier(wanted)) return Optional.empty();
        ProjectModel model = readOrEmpty();
        List<VariableModel> variables = new ArrayList<>(model.variables());
        if (indexOf(variables, wanted) >= 0) return Optional.empty();

        VariableModel declared = VariableModel.of(wanted, type, SdkValues.defaultValue(type))
                .withGroup(groupId);
        variables.add(declared);
        write(model.withVariables(variables));
        return Optional.of(rowOf(declared));
    }

    /** Removes the parameter {@code name} names. False when this group holds no such row. */
    public boolean remove(String name) {
        ProjectModel model = read();
        if (model == null) return false;
        List<VariableModel> variables = new ArrayList<>(model.variables());
        int at = indexOf(variables, name);
        if (at < 0) return false;
        variables.remove(at);
        write(model.withVariables(variables));
        return true;
    }

    /**
     * Renames a parameter, refusing a name that is blank, not an identifier, or already taken here.
     *
     * <p><b>The user's own source is not touched, and that is the honest behaviour rather than a gap.</b> A
     * bot reads a parameter as a field of a generated class, so a rename makes that field's old spelling stop
     * compiling — which is a readable error naming the line, at the moment the user next builds. Rewriting
     * their source from a settings window would be an edit they did not ask for in a file they own.
     */
    public Optional<ParameterRow> rename(String from, String to) {
        String wanted = to == null ? "" : to.trim();
        if (!isIdentifier(wanted)) return Optional.empty();
        return edit(from, (variables, at) -> {
            if (!variables.get(at).name().equals(wanted) && indexOf(variables, wanted) >= 0) return null;
            return variables.get(at).withName(wanted);
        });
    }

    /**
     * Retypes a parameter: its value resets to the new type's default, its bounds are dropped, and its
     * declared options survive only a change of <em>shape</em>.
     *
     * <p>The value does not carry across, deliberately — a date is not a number, and pretending otherwise
     * stores something the editor would have to explain away on the next open. Options survive one of and
     * many of over the same base type, because that is a question about how many may be picked rather than
     * about what may be picked; they do not survive a change of base type, whose values they no longer are.
     */
    public Optional<ParameterRow> retype(String name, ValueChoice type) {
        if (type == null) return Optional.empty();
        return edit(name, (variables, at) -> {
            VariableModel held = variables.get(at);
            // Compared by id, never by identity: a ValueType's identity is its persisted id, and two plugin
            // classloaders each holding their own copy of a class would make == mean nothing.
            List<String> options =
                    type.hasOptions() && type.type().equals(held.type().type()) ? held.options() : List.of();
            return new VariableModel(held.name(), type, SdkValues.defaultValue(type), held.description(),
                    held.tag(), held.visibility(), options, Range.NONE, groupId);
        });
    }

    /** Replaces the declared choices, pruning the stored value to what is still on offer. */
    public Optional<ParameterRow> setOptions(String name, List<String> options) {
        return edit(name, (variables, at) -> {
            VariableModel held = variables.get(at);
            List<String> declared = SdkValues.normalizeOptions(options, held.type(), held.bounds());
            // Built rather than copied with a `with…`: VariableModel deliberately has neither withOptions nor
            // withType, because both need the coercion rules — which are now here, one line down.
            return new VariableModel(held.name(), held.type(),
                    SdkValues.normalize(held.value(), held.type(), declared, held.bounds()),
                    held.description(), held.tag(), held.visibility(), declared, held.bounds(), groupId);
        });
    }

    /** Declares a range, clamping the stored value into it. */
    public Optional<ParameterRow> setBounds(String name, Range bounds) {
        return edit(name, (variables, at) -> {
            VariableModel held = variables.get(at);
            Range declared = bounds == null ? Range.NONE : bounds;
            return held.withBounds(declared)
                    .withValue(SdkValues.normalize(held.value(), held.type(), held.options(), declared));
        });
    }

    /** Files the parameter under a category of the owning group's — the rail inside the section. */
    public Optional<ParameterRow> setCategory(String name, String category) {
        return edit(name, (variables, at) -> variables.get(at).withTag(category == null ? "" : category));
    }

    /** Says whether whoever runs the bot is offered this parameter at all. */
    public Optional<ParameterRow> setVisibility(String name, Visibility visibility) {
        return edit(name, (variables, at) -> variables.get(at).withVisibility(visibility));
    }

    /** The sentence a user reads instead of the field name. */
    public Optional<ParameterRow> setDescription(String name, String description) {
        return edit(name, (variables, at) ->
                variables.get(at).withDescription(description == null ? "" : description));
    }

    /** What one verb does to one variable, or {@code null} to refuse the edit and write nothing. */
    private interface Change {
        VariableModel apply(List<VariableModel> variables, int at);
    }

    /**
     * Read, change one row, write, answer it — every verb above but {@link #declare} and {@link #remove}.
     *
     * <p>One place, because the alternative is nine copies of *read the file, find the row, put it back*, and
     * the copy that eventually forgets to write is the one nobody notices: the screen shows the change either
     * way, and only the next open disagrees.
     */
    private Optional<ParameterRow> edit(String name, Change change) {
        ProjectModel model = read();
        if (model == null) return Optional.empty();
        List<VariableModel> variables = new ArrayList<>(model.variables());
        int at = indexOf(variables, name);
        if (at < 0) return Optional.empty();

        VariableModel changed = change.apply(variables, at);
        if (changed == null) return Optional.empty();
        variables.set(at, changed);
        write(model.withVariables(variables));
        return Optional.of(rowOf(changed));
    }

    /**
     * A valid Java identifier, because a parameter's name is a generated field's.
     *
     * <p>Checked here rather than at the widget, so that every path into the file gets it — a dialog, a
     * paste, a future import.
     */
    private static boolean isIdentifier(String name) {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        }
        return true;
    }

    /** The index of the row {@code name} names within this group, or -1. */
    private int indexOf(List<VariableModel> variables, String name) {
        for (int i = 0; i < variables.size(); i++) {
            VariableModel variable = variables.get(i);
            if (variable.isIn(groupId) && variable.name().equals(name)) return i;
        }
        return -1;
    }

    /**
     * One variable as a row.
     *
     * <p>Component for component, because every one of them was already contract vocabulary — which is what
     * made the surface addable without the contract learning anything about this plugin. The only rename is
     * {@link VariableModel#tag()} arriving as {@link ParameterRow#category()}: the same field under the word
     * {@code ParameterGroup} settled on.
     */
    private static ParameterRow rowOf(VariableModel variable) {
        return ParameterRow.named(variable.name(), variable.type())
                .value(variable.value())
                .description(variable.description())
                .category(variable.tag())
                .visibility(variable.visibility())
                .options(variable.options())
                .bounds(variable.bounds())
                .build();
    }

    /**
     * The project's model, or an empty one — for {@link #declare}, which is the one verb that has to work on
     * a project whose file does not exist yet. Every other verb needs a row that is already there, so a
     * missing file makes them answer nothing rather than create one.
     */
    private ProjectModel readOrEmpty() {
        ProjectModel model = read();
        return model == null ? ProjectModel.empty() : model;
    }

    /** The project's model, or {@code null} when there is no readable file — a project with no parameters. */
    private ProjectModel read() {
        try {
            return Authoring.readModel(SdkVersion.latest(), resourcesDir);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void write(ProjectModel model) {
        try {
            Authoring.writeModel(SdkVersion.latest(), resourcesDir, model,
                    Authoring.readSchemaVersion(SdkVersion.latest(), resourcesDir));
        } catch (IOException e) {
            // A save that did not happen must not read as a save that did. The host contains this and leaves
            // the row as it was, which is what the user's next look at the window should show them.
            throw new UncheckedIOException("could not store the parameter into " + resourcesDir, e);
        }
    }
}
