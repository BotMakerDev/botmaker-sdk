package com.botmaker.sdk.authoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A named on/off selection of activities — a quick way to say "run these, skip the rest" without touching
 * the wiring. Applying a preset flips each activity's {@link ActivityModel#enabled()} flag: an activity is
 * enabled iff its name is in {@link #enabledActivities()}.
 *
 * <p>Two names are conventional and always offered by an editor whether or not the file lists them:
 * {@link #EVERYTHING} and {@link #NOTHING}. What the file holds is what the user saved on top of those.
 *
 * <p>Inert to the generator — nothing is emitted from a preset. It is stored because the file has one owner.
 *
 * @param name              the preset's display name
 * @param enabledActivities the activity names this preset turns on; all others are turned off
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PresetModel(String name, List<String> enabledActivities) {

    /** Conventional preset name: every activity on. */
    public static final String EVERYTHING = "Everything";
    /** Conventional preset name: every activity off. */
    public static final String NOTHING = "Nothing";

    public PresetModel {
        if (name == null) name = "";
        enabledActivities = enabledActivities == null ? List.of() : List.copyOf(enabledActivities);
    }

    /** True when this preset turns {@code activityName} on. */
    public boolean enables(String activityName) {
        return enabledActivities.contains(activityName);
    }

    /** The conventional "everything on" preset over the given activity names. */
    public static PresetModel everything(List<String> allActivityNames) {
        return new PresetModel(EVERYTHING, List.copyOf(allActivityNames));
    }

    /** The conventional "everything off" preset. */
    public static PresetModel nothing() {
        return new PresetModel(NOTHING, List.of());
    }

    /** A preset capturing exactly the currently-enabled activities of {@code model}. */
    public static PresetModel fromCurrent(String name, ProjectModel model) {
        return new PresetModel(name, model.activities().stream()
                .filter(ActivityModel::enabled).map(ActivityModel::name).distinct().toList());
    }
}
