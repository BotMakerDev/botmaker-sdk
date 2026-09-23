package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.ActivityBody;
import com.botmaker.sdk.api.bot.ActivityContext;
import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.bot.Watchdog;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Flows;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.internal.trace.Trace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The walk over a {@link Flow}: hold a current activity, run its body, and follow the edge its outcome names.
 *
 * <h2>Where a run ends</h2>
 *
 * <p>Two ways, and both end in {@link Bot#stop()}. The ordinary one is running out of wires: an outcome with
 * no edge, a disabled activity with no {@code DISABLED} edge, or an edge to an activity the flow does not
 * have. The other is {@link Flow.Limits#maxSteps()}, which counts hand-offs <em>between</em> activities — the
 * {@link Watchdog} only covers being stuck <em>inside</em> one.
 *
 * <h2>Switching an activity off mid-run</h2>
 *
 * <p>The flow's {@link Flow.Activity#enabled()} is the default; {@link #setEnabled} overrides it for the rest
 * of the process, which is how a body says "do this once, then stop". The overrides live here because the
 * walk is the only thing that reads them.
 */
public final class FlowWalker {

    private static final Map<String, Boolean> OVERRIDES = new ConcurrentHashMap<>();

    private FlowWalker() {}

    /**
     * Walks {@code flow} to its end. Does not return: it ends by calling {@link Bot#stop()}, which unwinds to
     * the supervisor.
     *
     * @param goHome the bot's own "get back to a known screen" step, or {@code null} for none
     */
    public static void run(Flow flow, Runnable goHome) {
        int maxSteps = flow.limits().maxSteps();
        int stepDelayMs = flow.limits().stepDelayMs();
        String current = start(flow);
        for (int steps = 0; current != null; steps++) {
            if (maxSteps > 0 && steps >= maxSteps) {
                Debug.error("[Flow] Gave up after " + maxSteps + " steps at '" + current
                        + "' — the flow is probably looping with no exit.");
                Bot.stop();
            }
            current = step(flow, current, goHome);
            Watchdog.checkpoint();
            // After the hand-off, not before it: this separates two activities rather than delaying the first.
            if (current != null && stepDelayMs > 0) {
                Wait.milliseconds(stepDelayMs);
            }
        }
        Bot.stop();
    }

    /** Whether the named activity runs this pass: a runtime override if one was made, else the flow's flag. */
    public static boolean active(String activity) {
        Boolean override = OVERRIDES.get(activity);
        return override != null ? override : Flows.enabled(activity);
    }

    /**
     * Overrides the named activity's flag for the rest of the process. A name the installed flow does not
     * have is a warning and a no-op, so a typo never stops a running bot.
     */
    public static void setEnabled(String activity, boolean enabled) {
        if (activity == null || Flows.installed().activity(activity) == null) {
            Debug.error("[Activity] setEnabled: the flow has no activity named '" + activity + "'. Ignoring.");
            return;
        }
        OVERRIDES.put(activity, enabled);
    }

    /** Test-only: forget every runtime override. */
    public static void clearOverrides() {
        OVERRIDES.clear();
    }

    /** {@link Flow#start()} when the flow has it, else its first activity, else {@code null}. */
    static String start(Flow flow) {
        if (flow.activity(flow.start()) != null) return flow.start();
        return flow.activities().isEmpty() ? null : flow.activities().getFirst().name();
    }

    /**
     * The activity after {@code name}, or {@code null} to end the run.
     *
     * <p>A disabled activity is not skipped <em>out of</em> the flow — the run still passes through it and
     * follows its {@code DISABLED} edge. An activity with no body yet ({@link ActivityBody#NONE}) takes the
     * same edge, for the same reason: it is on the canvas and it does nothing.
     */
    static String step(Flow flow, String name, Runnable goHome) {
        Flow.Activity activity = flow.activity(name);
        if (activity == null) return null;
        ActivityBody body = activity.body();
        if (body == null || body == ActivityBody.NONE || !active(name)) {
            return target(flow, name, Flow.Edge.DISABLED);
        }
        // Set for every activity, not only the ones that opt out: PopupGuard.enabled is process-global.
        PopupGuard.enabled(activity.popupCheck());
        // After the active() check: there is nothing to go home for if the activity won't run.
        if (activity.goHome() && goHome != null) goHome.run();
        return target(flow, name, execute(name, body).name());
    }

    /** Runs one body, logs the one line that makes a debug console read as a story, and answers its outcome. */
    private static Outcome execute(String name, ActivityBody body) {
        long startedAt = System.currentTimeMillis();
        Outcome outcome = body.run(new ActivityContext(name));
        if (outcome == null) outcome = Outcome.of(null);
        Debug.log("[Activity] " + name + " → " + outcome
                + " (" + Trace.elapsed(System.currentTimeMillis() - startedAt) + ")");
        return outcome;
    }

    /** Where the first edge leaving {@code from} on {@code outcome} leads, or {@code null}. */
    private static String target(Flow flow, String from, String outcome) {
        for (Flow.Edge edge : flow.edges()) {
            if (edge.from().equals(from) && edge.outcomeOrNext().equals(outcome)) return edge.to();
        }
        return null;
    }
}
