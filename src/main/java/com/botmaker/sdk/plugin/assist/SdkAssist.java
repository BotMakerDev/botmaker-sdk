package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AssistantTool;

import java.util.ArrayList;
import java.util.List;

/**
 * The SDK's tools for the AI assistant that drives Studio: look at what the bot sees ({@link LookTools}), make
 * its pictures and places ({@link PictureTools}) and tag the pictures ({@link TagTools}), shape its activity flow ({@link FlowTools}), and set where it
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
            TagTools.TOOLS, FlowTools.TOOLS, SetupTools.TOOLS);

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
              place, named <idea>_<where>; the same idea on another screen is another picture. On a screen
              reached for the first time, cut every button and marker a chore will need from one screenshot,
              not one per trip. Cut a button's image, not its label: a label's text changes with the game's
              language and shows through to a moving background; leave out the corner a badge (a red count
              or bell) comes and goes on, and any part that moves (crop_picture refuses a box that changes
              within a second: cut a still part). Tag each picture with the
              activity that uses it, or a custom tag (declare_tag) for home and popups: crop_picture takes
              tags, list_tags shows them.
            - Before writing a step, find it: describe looks, click, key and scroll act, and all four answer
              in text. Then write it in the activity's body and run_activity it.
            - A click can be missed and a screen can be slow (lag, a dropped connection), so a step repeats
              until the screen shows it worked, never clicks once and trusts it. The home method, for one:
              its marker is a picture no other screen shows (a sidebar icon shown everywhere is not one), and
              it stays visible under a popup. untilFind(marker, action) runs its action again at once, so
              the action presses Escape and then waits for the marker (ImageWaiter.waitFor, under a second):
              without that wait it presses again while the game answers. Then whileFindAny(the group of close
              and claim buttons) clears what still covers home. An Escape too many on home may open the
              game's exit dialog; its close button belongs in that group. A game that froze is not the
              body's to handle: the run's watchdog sees the same screen over and over and recovers through
              the home method.
            - Write a body foolproof, out of the loop methods that take a lambda: ImageFinder.whileFindAny,
              whileFindAll, ifFindAny (group, found -> …), untilFind(picture, action), whileFindCompare.
              Each pass looks at the screen again, so a missed click or a slow screen is retried, not
              trusted. Build the logic from what the lambda is handed (its parameter by var:<its name>):
              found.has(X) and found.get(X) say which pictures this frame shows, ImageClicker.click(
              found.get(X)) or clickLast() act on them without looking again. A group is
              ImageTemplateGroup.of(…, …). No loop blocks, no chain of separate find-then-click calls.
            - Click pictures, never places: no Mouse.click on a Spot or on x, y, which cannot tell whether
              they landed. A tab or button gets a picture.
            - Prove each step by a picture of its result, never by time: the loop's own picture coming or
              going, or ImageWaiter.waitFor / waitUntilGone of a picture only that screen or state shows
              (a close button is on every screen: not a marker). Screen.waitStable and Wait guess, they prove
              nothing.
            - Matching ignores colour: a lit button and its greyed-out twin both match (crop_picture says
              where else a cut is found). Cut the twin too and act with the compare forms, the lit one as
              good and the twin as bad: ImageFinder.whileFindCompare(good, bad, action), clickCompare.
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
