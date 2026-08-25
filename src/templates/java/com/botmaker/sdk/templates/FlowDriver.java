package com.botmaker.sdk.templates;

import com.botmaker.sdk.api.flow.FlowGraph;
import com.botmaker.sdk.api.flow.PopupCheck;
import com.botmaker.sdk.api.flow.Recovery;
import com.botmaker.sdk.templates.meta.Template;
/*<STUDIO:ACTIVITY_IMPORT>*/
import com.botmaker.sdk.templates.activities.*;
/*</STUDIO:ACTIVITY_IMPORT>*/

/**
 * Walks the Activity Flow drawn in BotMaker Studio. GENERATED — do not edit by hand; manage via
 * Project &rarr; Activity Flow.
 *
 * <p>Runs the current activity, then picks the next one from the outcome it reported. The run ends when the
 * reported outcome has no wire leaving it.
 *
 * <p>REGENERATED — rewritten from this template on every change to the flow. It holds the table and the two
 * numbers below and nothing else; the walk itself lives in the SDK, where it compiles once and is tested.
 */
@Template(id = "FLOW_DRIVER", kind = Template.Kind.REGENERATED, target = "FlowDriver.java")
public final class FlowDriver {

    /**
     * How many activities one run may hand off to before giving up. A flow is allowed to loop — that is how
     * a bot repeats — so this is what separates &quot;farming all night&quot; from a cycle with no way out.
     * Change it in Project &rarr; Activity Flow.
     */
    private static final int MAX_STEPS = /*<STUDIO:MAX_STEPS>*/ 1000 /*</STUDIO:MAX_STEPS>*/;

    /**
     * How long to pause between two activities, in milliseconds. A flow may loop, so an activity that
     * finishes in milliseconds can hand straight back to itself and never let go of the mouse — leaving no
     * gap in which to stop the bot. This is that gap. 0 disables it. Change it in
     * Project &rarr; Activity Flow.
     */
    private static final int STEP_DELAY_MS = /*<STUDIO:STEP_DELAY_MS>*/ 0 /*</STUDIO:STEP_DELAY_MS>*/;

    /** The flow, as drawn: which activity each node runs, and where each outcome it can report leads. */
    private static final FlowGraph FLOW = FlowGraph.of(
            /*<STUDIO:FLOW>*/
            "Example",
            FlowGraph.node("Example", ActivityRegistry.EXAMPLE, PopupCheck.ON, Recovery.NONE, null,
                    FlowGraph.route(ActivityStub.Outcome.NEXT, "Example"))
            /*</STUDIO:FLOW>*/);

    public static void run() {
        FlowGraph.walk(FLOW, MAX_STEPS, STEP_DELAY_MS, GoHome.INSTANCE::execute);
    }

    private FlowDriver() {}
}
