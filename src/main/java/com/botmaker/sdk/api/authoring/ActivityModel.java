package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.meta.Since;
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
@Since("1.2.0")
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
        return new VariableModel(name, ValueChoice.of(ValueType.YES_NO),
                List.of(Boolean.toString(enabled)), description, name, Visibility.EDITOR_ONLY,
                List.of(), Range.NONE);
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

    /** The implicit outcome first, then the declared ones — the order the {@code Outcome} enum is emitted in. */
    @JsonIgnore
    public List<String> allOutcomes() {
        return Stream.concat(Stream.of(FlowEdgeModel.NEXT_OUTCOME),
                outcomes.stream().filter(o -> !FlowEdgeModel.NEXT_OUTCOME.equals(o))).toList();
    }
}
