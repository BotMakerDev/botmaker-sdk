package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.bot.Watchdog;
import com.botmaker.sdk.api.flow.FlowGraph;
import com.botmaker.sdk.api.flow.PopupCheck;
import com.botmaker.sdk.api.flow.Recovery;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.api.util.Debug;

/**
 * The walk itself: hold a current node, run its activity, and go wherever the outcome it reported leads.
 *
 * <p>This is the code Studio used to write into every generated {@code FlowDriver}, identical in every
 * project — the loop, the step budget, the watchdog tick and the pause between activities. It lives here
 * because it is not a fact about anybody's bot; only {@link FlowGraph} is. Reached through
 * {@link FlowGraph#walk}, which is the {@code api} spelling a generated file writes:
 * {@code internal} is where an implementation lives, not something a bot names.
 *
 * <h2>Where a run ends</h2>
 *
 * <p>Two ways, and both end in {@link Bot#stop()}. The ordinary one is running out of wires: an outcome with
 * no route, a disabled activity with nothing to fall through to, or a route pointing at a node the graph does
 * not have. The other is the step budget, which counts hand-offs <em>between</em> activities — a bound
 * nothing else supplies, since a flow that loops is a flow working as intended and the {@link Watchdog} only
 * covers being stuck <em>inside</em> one activity.
 */
public final class FlowWalker {

    private FlowWalker() {}

    /**
     * Runs {@code graph} to its end. Does not return: it ends by calling {@link Bot#stop()}, which unwinds to
     * the supervisor.
     *
     * @param graph       the table Studio generated
     * @param maxSteps    how many hand-offs one run may make before giving up
     * @param stepDelayMs the pause between two activities, in milliseconds; 0 disables it
     * @param goHome      the bot's own "get back to a known screen" step, or {@code null} for none
     */
    public static void walk(FlowGraph graph, int maxSteps, int stepDelayMs, Runnable goHome) {
        String node = graph.start();
        for (int steps = 0; node != null; steps++) {
            if (steps >= maxSteps) {
                Debug.error("[Flow] Gave up after " + maxSteps + " steps at '" + node
                        + "' — the flow is probably looping with no exit.");
                Bot.stop();
            }
            node = step(graph, node, goHome);
            Watchdog.checkpoint();
            // After the hand-off, not before it: this separates two activities rather than delaying the
            // first, and a run that has just ended shouldn't sit here waiting.
            if (node != null && stepDelayMs > 0) {
                Wait.milliseconds(stepDelayMs);
            }
        }
        Bot.stop();
    }

    /** The next node after {@code name}, or {@code null} to end the run. */
    private static String step(FlowGraph graph, String name, Runnable goHome) {
        FlowGraph.Node node = graph.nodeNamed(name);
        if (node == null) return null;
        // A disabled activity isn't skipped out of the flow — the flow still passes through it, it just
        // doesn't do anything, so it follows the wire it would have taken with nothing to report.
        if (!node.activity().active()) return node.whenDisabled();
        // Set for every node, not just the ones that opt out: PopupGuard.enabled is process-global, so a node
        // that said nothing would inherit whatever the node before it left it set to.
        PopupGuard.enabled(node.popupCheck() == PopupCheck.ON);
        // After the active() check, not before: there is nothing to go home for if the activity won't run.
        if (node.recovery() == Recovery.GO_HOME && goHome != null) goHome.run();
        return node.target(node.activity().execute());
    }
}
