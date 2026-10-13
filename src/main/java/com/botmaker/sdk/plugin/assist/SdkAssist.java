package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AssistantTool;

import java.util.ArrayList;
import java.util.List;

/**
 * The SDK's tools for the AI assistant that drives Studio: look at what the bot sees ({@link LookTools}), make
 * its pictures and places ({@link PictureTools}), shape its activity flow ({@link FlowTools}), and set where it
 * looks and how it runs ({@link SetupTools}).
 *
 * <p>Coordinates are the <b>screenshot's</b> pixels: {@code (0, 0)} is the top-left of what {@code screenshot}
 * returned, so the assistant crops what it was shown. Every write goes through the same path as the SDK's own
 * windows, on the FX thread ({@link FxCall}): the picture files and their {@code Pictures} constants, the
 * {@code Points} and {@code Regions} constants, the flow and its {@code Activities}/{@code Outcomes} constants,
 * {@code Sdk.captureSource} and {@code Sdk.settings()}, and this machine's launch target. {@link ActTools}
 * clicks, taps a key or scrolls on the game the look tools see, and says in text what changed; on the user's own
 * desktop only when asked.
 */
public final class SdkAssist {

    /** Every tool, by group. */
    public static final List<AssistantTool<?>> ALL = join(LookTools.TOOLS, ActTools.TOOLS, PictureTools.TOOLS,
            FlowTools.TOOLS, SetupTools.TOOLS);

    /**
     * How a bot on the SDK is laid out, said to the assistant beside these tools: a bot is its activities, and a
     * flow says which runs next. What the gamebot template's {@code Gamebot} javadoc tells a person, said as rules.
     */
    public static final String GUIDE = """
            A bot is a set of activities and a flow that says which runs next.
            - One activity per chore (collect the loot, claim the quests, fight the boss). add_activity adds
              its card and its enum in Activities, whose constants are the outcomes it can report (NEXT to
              start with). Its work is a public static body() in a class of its own, returning that enum. A
              body does one unit of work, returns, and never loops forever: the flow repeats it.
            - The flow (read_flow, connect, set_start, add_flow) wires each outcome to the next activity. Put
              the order of chores there, never in a body calling another body.
            - Navigation lives in one place: the home method takes the game back to the screen every
              activity starts from, safe to call at any moment. Make it with add_method, then name it with
              set_flow_method. An activity that needs another screen opens it from there in its own body.
            - Pictures, points and regions are the bot's constants (Pictures.CLAIM_RESEARCH): one per idea and
              place, named <idea>_<where>; the same idea on another screen is another picture.
            - Before writing a step, find it: describe looks, click, key and scroll act, and all four answer
              in text. Then write it in the activity's body and run_activity it.
            - A run's choices (which flow, which activity is on) are @Param fields, never run properties.
            """;

    private SdkAssist() {}

    @SafeVarargs
    private static List<AssistantTool<?>> join(List<AssistantTool<?>>... groups) {
        List<AssistantTool<?>> all = new ArrayList<>();
        for (List<AssistantTool<?>> group : groups) all.addAll(group);
        return List.copyOf(all);
    }
}
