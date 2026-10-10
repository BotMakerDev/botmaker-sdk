package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.Labels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A flow as the editor changes it: the {@link Flow} value, plus each activity's exits — the constants of its
 * enum in the bot's {@code Activities} class, as labels (2026-10-10).
 *
 * <p>The flow no longer lists what an activity can report: the activity's enum does, and the body returns it.
 * So the editor reads the exits off the project ({@link #read}), changes them beside the flow, and the save
 * brings the enum in step ({@link FlowConstants}) before it writes the flow that names its constants.
 *
 * @param flow  the value {@code Sdk.flow()} returns
 * @param exits each activity's label → its outcomes' labels, in the enum's order; every activity has one
 */
public record Board(Flow flow, Map<String, List<String>> exits) {

    public Board {
        flow = flow == null ? Flow.NONE : flow;
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (exits != null) exits.forEach((activity, outcomes) -> copy.put(activity, List.copyOf(outcomes)));
        exits = Collections.unmodifiableMap(copy);
    }

    /**
     * {@code flow} with each activity's exits as the project declares them: the constants of its enum, then any
     * outcome a wire leaves on that the enum does not hold yet. An activity with no enum yet has {@link Arrow#NEXT}.
     */
    public static Board read(PluginValues values, Flow flow) {
        Map<String, List<String>> declared = FlowConstants.declared(values);
        Map<String, List<String>> exits = new LinkedHashMap<>();
        for (Flow.Step step : flow.steps()) {
            List<String> own = new ArrayList<>();
            for (String constant : declared.getOrDefault(step.activity().name(), List.of())) {
                own.add(Labels.of(constant));
            }
            for (Flow.Edge edge : flow.edges()) {
                if (edge.from().equals(step.activity()) && !own.contains(edge.on().label())) own.add(edge.on().label());
            }
            if (own.isEmpty()) own.add(Arrow.NEXT);
            exits.put(step.label(), own);
        }
        return new Board(flow, exits);
    }

    /** This board with {@code flow}, its exits kept. */
    public Board with(Flow flow) {
        return new Board(flow, exits);
    }

    /** The outcomes of the activity labelled {@code activity}, or none. */
    public List<String> exitsOf(String activity) {
        return exits.getOrDefault(activity, List.of());
    }
}
