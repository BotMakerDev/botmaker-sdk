package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The activities the person running the bot switched off, as the bot's {@code skip} parameter lists them:
 * {@code Set.of(Activities.Rest.class)}.
 *
 * <p>Only a step drawn {@code .switchable()} is the runner's to switch, in the flow or in any of its sub-flows. One
 * listed that is not is ignored, and the debug trace says so: the same list serves every flow, and an activity
 * switchable in another flow is kept for when that flow runs. The walk reads the list
 * ({@link InstalledRun#skipped}), so a flow and its sub-flows stay the values the bot declares.
 */
public final class SkippedActivities {

    private SkippedActivities() {}

    /** The activities {@code skip} lists, with one trace line for each that {@code flow} will not switch off. */
    public static Set<Activity> of(Flow flow, Collection<Class<? extends Outcome>> skip) {
        if (skip == null || skip.isEmpty()) return Set.of();
        Set<Activity> skipped = skip.stream().filter(Objects::nonNull).map(Activity::of)
                .collect(Collectors.toUnmodifiableSet());
        List<Flow.Step> steps = FlowTree.steps(flow);
        for (Activity activity : skipped) {
            List<Flow.Step> running = steps.stream().filter(step -> step.node().equals(activity)).toList();
            if (running.isEmpty()) {
                Debug.log(activity.label() + " is listed to skip, but this flow has no such activity.");
            } else if (running.stream().noneMatch(Flow.Step::runnerSwitch)) {
                Debug.log(activity.label() + " is listed to skip, but in this flow it is not an activity the "
                        + "runner may switch, so it runs. Tick \"Runner may switch this off\" on its card to "
                        + "allow it.");
            } else if (running.stream().anyMatch(step -> !step.runnerSwitch())) {
                Debug.log(activity.label() + " is off where its card lets the runner switch it, as the bot's skip "
                        + "parameter says, and runs where it does not.");
            } else {
                Debug.log(activity.label() + " is off, as the bot's skip parameter says.");
            }
        }
        return skipped;
    }
}
