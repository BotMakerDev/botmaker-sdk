package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The activities the person running the bot switched off, as the bot's {@code skip} parameter lists them:
 * {@code Set.of(Activities.Rest.class)}.
 *
 * <p>Only a step drawn {@code .switchable()} is the runner's to switch. One listed that is not is ignored, and the
 * debug trace says so: the same list serves every flow, and an activity switchable in another flow is kept for
 * when that flow runs.
 */
public final class SkippedActivities {

    private SkippedActivities() {}

    /** {@code flow} with each switchable step whose activity {@code skip} lists switched off. */
    public static Flow applied(Flow flow, Collection<Class<? extends Outcome>> skip) {
        if (skip == null || skip.isEmpty()) return flow;
        Set<Activity> skipped = skip.stream().filter(Objects::nonNull).map(Activity::of)
                .collect(Collectors.toSet());
        for (Activity activity : skipped) {
            if (flow.step(activity) == null) {
                Debug.log(activity.label() + " is listed to skip, but this flow has no such activity.");
            }
        }
        List<Flow.Step> steps = new ArrayList<>();
        boolean changed = false;
        for (Flow.Step step : flow.steps()) {
            if (skipped.contains(step.activity())) {
                if (!step.runnerSwitch()) {
                    Debug.log(step.label() + " is listed to skip, but in this flow it is not an activity the "
                            + "runner may switch, so it runs. Tick \"Runner may switch this off\" on its card to "
                            + "allow it.");
                } else if (step.enabled()) {
                    Debug.log(step.label() + " is off, as the bot's skip parameter says.");
                    step = new Flow.Step(step.activity(), step.body(), step.description(), false, step.goHome(),
                            step.popupCheck(), step.runnerSwitch(), step.whenOff());
                    changed = true;
                }
            }
            steps.add(step);
        }
        return changed ? new Flow(steps, flow.edges(), flow.start(), flow.limits()) : flow;
    }
}
