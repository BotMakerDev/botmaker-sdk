package com.botmaker.sdk.internal.plugin;

import com.botmaker.plugin.api.ParameterEdit;
import com.botmaker.plugin.api.ParameterRow;
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
 * <h2>Nothing here coerces</h2>
 *
 * <p>A value arrives as text and is stored as text. Clamping to a declared range, pruning a list to the
 * options still on offer and resetting a value when a variable is retyped are the <em>editor's</em> rules and
 * stay where a user can watch them happen — which is the same split {@link VariableModel} already documents
 * against the editor's own record. So {@link #apply} answers the row it stored, and the row it stored is what
 * it was handed.
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

        VariableModel stored = variables.get(at).withValue(edit.value());
        variables.set(at, stored);
        write(model.withVariables(variables));
        return Optional.of(rowOf(stored));
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
