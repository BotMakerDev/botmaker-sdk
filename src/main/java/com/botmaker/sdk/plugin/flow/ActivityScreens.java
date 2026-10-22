package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.types.FlowTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which activity works on which screen of the map, as the bot's flows say it ({@code .on(Screens.TAVERN)}): what 🗺
 * lights on a card, records into, and what the input picker offers first in that activity's body.
 */
public final class ActivityScreens {

    private ActivityScreens() {}

    /**
     * An activity of the bot's flows: its label, its body as the flow names it ({@code Tavern::body}), and the screen
     * constant its card says it works on, null when it says none or names one the map lacks.
     */
    public record Target(String label, String body, String screen) {}

    /** Every activity of the bot's flows that has a body, once each, in flow then card order. */
    public static List<Target> targets(PluginValues values, ScreenGraph graph) {
        Map<String, Target> found = new LinkedHashMap<>();
        for (Member flow : FlowValue.FLOWS.members(values)) {
            Flow read = FlowValue.FLOWS.read(values, flow).orElse(Flow.NONE);
            for (Flow.Step step : read.steps()) {
                String body = step.activity().isNone() ? "" : FlowTypes.sourceOf(step.body());
                if (body.isEmpty()) continue;
                String screen = step.screen() == null ? null : graph.constantOf(step.screen()).orElse(null);
                found.putIfAbsent(body, new Target(step.label(), body, screen));
            }
        }
        return List.copyOf(found.values());
    }

    /** The labels of the activities that work on each screen, by its constant. */
    public static Map<String, List<String>> byScreen(List<Target> targets) {
        Map<String, List<String>> by = new LinkedHashMap<>();
        for (Target target : targets) {
            if (target.screen() != null) by.computeIfAbsent(target.screen(), s -> new ArrayList<>()).add(target.label());
        }
        return by;
    }

    /** The screen the activity whose body is {@code body} ({@code Tavern::body}) works on, or empty. */
    public static Optional<String> screenOf(List<Target> targets, String body) {
        return targets.stream().filter(t -> t.body().equals(body) && t.screen() != null).map(Target::screen).findFirst();
    }
}
