package com.botmaker.sdk.authoring;

import com.botmaker.plugin.api.value.Range;
import com.botmaker.plugin.api.value.ValueChoice;
import com.botmaker.plugin.api.value.Visibility;
import com.botmaker.sdk.internal.authoring.SdkValueTypes;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.stream.Stream;

/**
 * One activity: a named unit of work the bot can be in, and the stub class the generator emits for it.
 *
 * <p>{@link #outcomes()} are the <em>extra</em> ways it can end. Every activity also has the one it does not
 * declare — {@link FlowEdgeModel#NEXT_OUTCOME}, "nothing special to report, carry on" — which is emitted as
 * the first constant of the activity's {@code Outcome} enum and is what a wire with no outcome names. That
 * is why {@link #allOutcomes()} leads with it rather than appending it.
 *
 * <p>There is a second undeclared outcome, {@link FlowEdgeModel#DISABLED_OUTCOME}, and it is deliberately
 * <em>not</em> in that list: it is a port the flow can be wired from, not a constant the activity can report.
 * {@link #flowPorts()} is the list that includes it, and the two methods exist separately so the generated
 * enum and the editor's ports can differ in the one place they must.
 *
 * <p>{@link #goHome()} and {@link #popupCheck()} are boxed because absent and {@code false} are different
 * answers: a file written before the field existed must take the project's default, not a silent "no". Both
 * default to {@code true} when absent, which is what every project written before them behaved as.
 *
 * @param name        the activity's name; also the stub class's name and its registry key
 * @param enabled     whether the flow may enter it at all
 * @param description a human-readable note; may be empty
 * @param outcomes    the declared outcomes, beyond the implicit one
 * @param goHome      whether the driver returns home before entering it
 * @param popupCheck  whether the driver dismisses popups before entering it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityModel(String name, boolean enabled, String description, List<String> outcomes,
                            Boolean goHome, Boolean popupCheck) {

    public ActivityModel {
        if (name == null) name = "";
        if (description == null) description = "";
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        if (goHome == null) goHome = Boolean.TRUE;
        if (popupCheck == null) popupCheck = Boolean.TRUE;
    }

    /** An enabled activity with nothing declared beyond its name. */
    public static ActivityModel of(String name) {
        return new ActivityModel(name, true, "", List.of(), null, null);
    }

    /**
     * A fresh activity with the given name and description, <b>disabled</b>.
     *
     * <p>The opposite default to {@link #of}, and deliberately so: {@code of} builds a model in code (a test,
     * a generator fixture) where "on" is the useful state, while this is what an editor calls when a person
     * has just typed a name into a dialog. A new activity that starts running the moment it is created is a
     * bot doing something nobody asked for yet.
     */
    public static ActivityModel create(String name, String description) {
        return new ActivityModel(name, false, description, List.of(), null, null);
    }

    /**
     * The synthetic {@link VariableModel} for this activity's enable flag — the {@code boolean} field the
     * generated {@code Activities} class carries for it.
     *
     * <p>Tagged with the activity's own name, so it lists with that activity's variables, and
     * {@link Visibility#EDITOR_ONLY} because whoever runs the bot is already offered every activity's own
     * switch; a second one under a tag heading is the same flag twice.
     */
    @JsonIgnore
    public VariableModel enabledVariable() {
        return new VariableModel(name, ValueChoice.of(SdkValueTypes.YES_NO),
                List.of(Boolean.toString(enabled)), description, name, Visibility.EDITOR_ONLY,
                List.of(), Range.NONE, com.botmaker.plugin.api.ParameterGroup.DEFAULT_ID);
    }

    // ---- copies -----------------------------------------------------------------------------------------

    public ActivityModel withEnabled(boolean newEnabled) {
        return new ActivityModel(name, newEnabled, description, outcomes, goHome, popupCheck);
    }

    public ActivityModel withDescription(String newDescription) {
        return new ActivityModel(name, enabled, newDescription, outcomes, goHome, popupCheck);
    }

    public ActivityModel withOutcomes(List<String> newOutcomes) {
        return new ActivityModel(name, enabled, description, newOutcomes, goHome, popupCheck);
    }

    public ActivityModel withGoHome(boolean newGoHome) {
        return new ActivityModel(name, enabled, description, outcomes, newGoHome, popupCheck);
    }

    public ActivityModel withPopupCheck(boolean newPopupCheck) {
        return new ActivityModel(name, enabled, description, outcomes, goHome, newPopupCheck);
    }

    /**
     * The implicit outcome first, then the declared ones — the order the {@code Outcome} enum is emitted in.
     *
     * <p>{@link FlowEdgeModel#DISABLED_OUTCOME} is filtered out for the same reason
     * {@link FlowEdgeModel#NEXT_OUTCOME} is de-duplicated: both are outcomes every activity has already, so a
     * file that declares one must not emit it twice. Only {@code NEXT} is then re-added, because only
     * {@code NEXT} is an enum constant.
     */
    @JsonIgnore
    public List<String> allOutcomes() {
        return Stream.concat(Stream.of(FlowEdgeModel.NEXT_OUTCOME), outcomes.stream()
                .filter(o -> !FlowEdgeModel.NEXT_OUTCOME.equals(o))
                .filter(o -> !FlowEdgeModel.DISABLED_OUTCOME.equals(o))).toList();
    }

    /**
     * Every port the flow can be wired from: {@link #allOutcomes()}, then
     * {@link FlowEdgeModel#DISABLED_OUTCOME} last.
     *
     * <p>Last rather than first because it is the exceptional one — every other port is a way the activity
     * <em>finished</em>, and this is the one for it never having run. It is the single source of the card's
     * output ports, so the ports and the wires an editor is allowed to keep cannot drift.
     */
    @JsonIgnore
    public List<String> flowPorts() {
        return Stream.concat(allOutcomes().stream(), Stream.of(FlowEdgeModel.DISABLED_OUTCOME)).toList();
    }
}
