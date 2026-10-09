package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;

import java.util.List;

/**
 * One preset while the dialog is open: a name and the labels of the activities it switches on. Saved as a
 * {@link Flow.Preset} naming the bot's {@code Activities} constants; labels here for the reason {@link Arrow}
 * holds them.
 */
public record Selection(String name, List<String> activities) {

    public Selection {
        name = name == null ? "" : name;
        activities = activities == null ? List.of() : List.copyOf(activities);
    }

    /** The selection a stored preset is shown as. */
    public static Selection of(Flow.Preset preset) {
        return new Selection(preset.name(), preset.activities().stream().map(Activity::label).toList());
    }

    /** Whether {@code activity} is one this selection switches on. */
    public boolean enables(String activity) {
        return activities.contains(activity);
    }

    /** The preset this selection is saved as. */
    public Flow.Preset toPreset() {
        return Flow.preset(name, activities.stream().map(FlowNames::activity).toList());
    }
}
