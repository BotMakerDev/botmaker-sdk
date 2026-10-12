package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;
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
 * no edge, a disabled activity with no off-wire, or an edge to an activity the flow does not
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
     * Walks {@code flow} to its end. Does not return: it ends by calling {@link Bot#stop()}, which unwinds to the
     * supervisor.
     *
     * <p>{@code goHome}, the run's {@link Run#home()} or {@code null}, runs before each step that goes home.
     *
     * <p>A sub-flow read before its declaration is null; the run refuses it before any step, naming the flow that
     * holds it, rather than ending at that card an hour in.
     */
    public static void run(Flow flow, Runnable goHome) {
        Flow holder = FlowTree.holderOfUnread(flow);
        if (holder != null) {
            String name = InstalledRun.nameOf(holder);
            Debug.error("[Flow] " + (name.isEmpty() ? "A flow" : name) + " runs a sub-flow that is not declared yet "
                    + "where it is read: in Flows, declare that flow above " + (name.isEmpty() ? "it" : name) + ".");
            Bot.stop();
        }
        walk(flow, goHome, false);
        Bot.stop();
    }

    /**
     * Walks {@code flow} until no wire leads on, and answers the outcome it ended on: one nothing in {@code flow}
     * wires, which a sub-flow card's outer wires take up. {@code null} when it ended otherwise — a card reporting
     * nothing, switched off with no off-wire, or a flow with nothing in it. Hitting the step limit stops the run.
     *
     * @param inner whether {@code flow} is a sub-flow's, whose unwired outcome is its card's exit rather than the
     *              end of the run: the trace then says so as a step, not as an error
     */
    static Outcome walk(Flow flow, Runnable goHome, boolean inner) {
        int maxSteps = flow.limits().maxSteps();
        int stepDelayMs = flow.limits().stepDelayMs();
        Flow.Node current = start(flow);
        for (int steps = 0; current != null; steps++) {
            if (maxSteps > 0 && steps >= maxSteps) {
                Debug.error("[Flow] Gave up after " + maxSteps + " steps at '" + current.label()
                        + "' — the flow is probably looping with no exit.");
                Bot.stop();
            }
            Hop hop = step(flow, current, goHome, inner);
            Watchdog.checkpoint();
            if (hop.next() == null) return hop.end();
            current = hop.next();
            // After the hand-off, not before it: this separates two activities rather than delaying the first.
            if (stepDelayMs > 0) {
                Wait.milliseconds(stepDelayMs);
            }
        }
        return null;
    }

    /**
     * One hand-off: the card that runs next, or {@code null} with the outcome the flow ended on ({@code null} when
     * it ended on none).
     */
    record Hop(Flow.Node next, Outcome end) {

        static Hop to(Flow.Node next) {
            return new Hop(next == null || next.isNone() ? null : next, null);
        }

        static Hop endedOn(Outcome outcome) {
            return new Hop(null, outcome);
        }
    }

    /** Whether the activity runs this pass: a runtime override if one was made, else the flow's flag. */
    public static boolean active(Activity activity) {
        Boolean override = activity == null ? null : OVERRIDES.get(activity);
        return override != null ? override : InstalledRun.enabled(activity);
    }

    /** Whether {@code step} runs this pass: a runtime override of its activity, else its own flag and the runner's. */
    static boolean active(Flow.Step step) {
        Boolean override = step.node() instanceof Activity activity ? OVERRIDES.get(activity) : null;
        return override != null ? override : step.enabled() && !InstalledRun.skipped(step);
    }

    /**
     * Overrides the activity's flag for the rest of the process. One the installed flow does not have is a
     * warning and a no-op, so a stale constant never stops a running bot.
     */
    public static void setEnabled(Activity activity, boolean enabled) {
        if (activity == null || FlowTree.find(InstalledRun.flow(), activity) == null) {
            Debug.error("[Activity] setEnabled: the flow has no activity '"
                    + (activity == null ? "null" : activity.label()) + "'. Ignoring.");
            return;
        }
        OVERRIDES.put(activity, enabled);
    }

    /** Test-only: forget every runtime override. */
    public static void clearOverrides() {
        OVERRIDES.clear();
    }

    /** {@link Flow#start()} when the flow has it, else its first card, else {@code null}. */
    static Flow.Node start(Flow flow) {
        if (flow.step(flow.start()) != null) return flow.start();
        return flow.steps().isEmpty() ? null : flow.steps().getFirst().node();
    }

    /**
     * The card after {@code node}, or the outcome {@code flow} ends on.
     *
     * <p>A disabled card is not skipped <em>out of</em> the flow — the run still passes through it and follows
     * its off-wire ({@link Flow.Step#whenOff()}). An activity with no body yet ({@link ActivityBody#none()}) takes
     * the same wire, for the same reason: it is on the canvas and it does nothing.
     *
     * <p>A sub-flow card walks its flow to its end, under that flow's own limits, and takes the wire on the
     * outcome it ended on. An outcome with no wire ends this flow, and one line says so: the body returns its own
     * enum, so it is one of the card's exits that nobody connected — or, inside a sub-flow, one the card's own
     * wires take up.
     */
    static Hop step(Flow flow, Flow.Node node, Runnable goHome, boolean inner) {
        Flow.Step step = flow.step(node);
        if (step == null) return Hop.to(null);
        boolean noBody = node instanceof Activity && (step.body() == null || step.body() == ActivityBody.none());
        if (noBody || !active(step)) return Hop.to(step.whenOff());
        // Set for every card, not only the ones that opt out: PopupGuard.enabled is process-global.
        PopupGuard.enabled(step.popupCheck());
        // After the active() check: there is nothing to go home for if the card won't run.
        if (step.goHome() && goHome != null) goHome.run();
        Outcome outcome = switch (node) {
            case Activity activity -> execute(step, step.body());
            case Flow.Sub sub -> enter(sub, goHome);
        };
        if (outcome == null || Outcome.NONE.equals(outcome)) {
            Debug.error("[Activity] " + step.label() + " reported nothing, so the run ends here.");
            return Hop.to(null);
        }
        Flow.Node next = FlowTree.wired(flow, node, outcome);
        if (next == null) {
            if (inner) {
                Debug.log("[Activity] " + step.label() + " reported '" + outcome.label() + "', an exit of "
                        + InstalledRun.nameOf(flow) + ".");
            } else {
                Debug.error("[Activity] " + step.label() + " reported '" + outcome.label() + "', and nothing is"
                        + " wired to it in the Activity Flow, so the run ends here.");
            }
            return Hop.endedOn(outcome);
        }
        return Hop.to(next);
    }

    /** Walks a sub-flow card's flow, with one trace line going in and one coming out, and answers where it ended. */
    private static Outcome enter(Flow.Sub sub, Runnable goHome) {
        Debug.log("[Flow] → " + sub.label());
        Outcome ended = walk(sub.inner(), goHome, true);
        Debug.log("[Flow] ← " + sub.label() + (ended == null ? ", on nothing" : " on '" + ended.label() + "'"));
        return ended;
    }

    /**
     * Runs one body, logs the one line that makes a debug console read as a story, and answers its outcome. A
     * body that throws is logged as an error with its stack, debugging on or off, and the throw goes on to
     * {@code Bot}'s recovery: the trace says which activity failed, where the console alone said only "Crashed".
     */
    private static Outcome execute(Flow.Step step, ActivityBody<?> body) {
        long startedAt = System.currentTimeMillis();
        Activity outer = CURRENT.get();
        CURRENT.set(step.activity());   // execute runs activities only: a sub-flow card is walked, not run
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
        Debug.log("[Activity] " + step.label() + " → " + (outcome == null ? "nothing" : outcome.label())
                + " ("+ Trace.elapsed(System.currentTimeMillis() - startedAt) + ")");
        return outcome;
    }

    /** {@code Bot.stop()}'s throw, which ends a run on purpose and is no failure. Its class is private to {@code Bot}. */
    private static boolean isStop(Throwable e) {
        return e.getClass().getName().equals(STOP);
    }

    private static final String STOP = "com.botmaker.sdk.api.bot.Bot$BotStoppedException";
}
