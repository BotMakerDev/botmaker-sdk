package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.internal.bot.Watchdog;
import com.botmaker.sdk.internal.observe.Bots;
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
 * <p>The flow's {@link Flow.Step#enabled()} is the default; {@link #setEnabled} overrides it for the rest of the
 * process, which is how a body says "do this once, then stop". The overrides live here because the walk is the
 * only thing that reads them.
 */
public final class FlowWalker {

    private static final Map<Activity, Boolean> OVERRIDES = new ConcurrentHashMap<>();

    /** The activity whose body this thread is running. */
    private static final ThreadLocal<Activity> CURRENT = new ThreadLocal<>();

    private FlowWalker() {}

    /** The activity whose body is running on this thread, or {@code null} outside one. */
    public static Activity current() {
        return CURRENT.get();
    }

    /**
     * Walks {@code flow} to its end. Does not return: it ends by calling {@link Bot#stop()}, which unwinds to
     * the supervisor.
     *
     * @param goHome the bot's own "get back to a known screen" step, or {@code null} for none
     */
    public static void run(Flow flow, Runnable goHome) {
        int maxSteps = flow.limits().maxSteps();
        int stepDelayMs = flow.limits().stepDelayMs();
        Activity current = start(flow);
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

    /** Whether the activity runs this pass: a runtime override if one was made, else the flow's flag. */
    public static boolean active(Activity activity) {
        Boolean override = activity == null ? null : OVERRIDES.get(activity);
        return override != null ? override : Flows.enabled(activity);
    }

    /**
     * Overrides the activity's flag for the rest of the process. One the installed flow does not have is a
     * warning and a no-op, so a stale constant never stops a running bot.
     */
    public static void setEnabled(Activity activity, boolean enabled) {
        if (activity == null || Flows.installed().step(activity) == null) {
            Debug.error("[Activity] setEnabled: the flow has no activity '" + activity + "'. Ignoring.");
            return;
        }
        OVERRIDES.put(activity, enabled);
    }

    /** Test-only: forget every runtime override. */
    public static void clearOverrides() {
        OVERRIDES.clear();
    }

    /** {@link Flow#start()} when the flow has it, else its first activity, else {@code null}. */
    static Activity start(Flow flow) {
        if (flow.step(flow.start()) != null) return flow.start();
        return flow.steps().isEmpty() ? null : flow.steps().getFirst().activity();
    }

    /**
     * The activity after {@code activity}, or {@code null} to end the run.
     *
     * <p>A disabled activity is not skipped <em>out of</em> the flow — the run still passes through it and
     * follows its {@code DISABLED} edge. An activity with no body yet ({@link ActivityBody#NONE}) takes the
     * same edge, for the same reason: it is on the canvas and it does nothing.
     *
     * <p>An unwired outcome the step does not declare is not refused: it ends the run, as any unwired outcome
     * does, and one line says the activity reported something its card never offered.
     */
    static Activity step(Flow flow, Activity activity, Runnable goHome) {
        Flow.Step step = flow.step(activity);
        if (step == null) return null;
        ActivityBody body = step.body();
        if (body == null || body == ActivityBody.NONE || !active(activity)) {
            return target(flow, activity, Outcome.DISABLED);
        }
        // Set for every activity, not only the ones that opt out: PopupGuard.enabled is process-global.
        PopupGuard.enabled(step.popupCheck());
        // After the active() check: there is nothing to go home for if the activity won't run.
        if (step.goHome() && goHome != null) goHome.run();
        Outcome outcome = execute(step, body);
        Activity next = target(flow, activity, outcome);
        if (next == null && !outcome.equals(Outcome.NEXT) && !step.outcomes().contains(outcome)) {
            Debug.error("[Activity] " + step.label() + " reported '" + outcome + "', which it does not declare in "
                    + "the Activity Flow — nothing is wired to it, so the run ends here.");
        }
        return next;
    }

    /**
     * Runs one body, logs the one line that makes a debug console read as a story, and answers its outcome. A
     * body that throws is logged as an error with its stack, debugging on or off, and the throw goes on to
     * {@code Bot}'s recovery: the trace says which activity failed, where the console alone said only "Crashed".
     */
    private static Outcome execute(Flow.Step step, ActivityBody body) {
        long startedAt = System.currentTimeMillis();
        Activity outer = CURRENT.get();
        CURRENT.set(step.activity());
        Bots.fireActivity(step.label());
        Outcome outcome;
        try {
            outcome = body.run();
        } catch (RuntimeException | Error e) {
            if (!isStop(e)) {
                Debug.error("[Activity] " + step.label() + " threw " + e + " after "
                        + Trace.elapsed(System.currentTimeMillis() - startedAt), e);
            }
            throw e;
        } finally {
            CURRENT.set(outer);
        }
        if (outcome == null) outcome = Outcome.NEXT;
        Debug.log("[Activity] " + step.label() + " → " + outcome
                + " (" + Trace.elapsed(System.currentTimeMillis() - startedAt) + ")");
        return outcome;
    }

    /** {@code Bot.stop()}'s throw, which ends a run on purpose and is no failure. Its class is private to {@code Bot}. */
    private static boolean isStop(Throwable e) {
        return e.getClass().getName().equals(STOP);
    }

    private static final String STOP = "com.botmaker.sdk.api.bot.Bot$BotStoppedException";

    /** Where the first edge leaving {@code from} on {@code outcome} leads, or {@code null}. */
    private static Activity target(Flow flow, Activity from, Outcome outcome) {
        for (Flow.Edge edge : flow.edges()) {
            if (edge.from().equals(from) && edge.outcome().equals(outcome)) return edge.to();
        }
        return null;
    }
}
