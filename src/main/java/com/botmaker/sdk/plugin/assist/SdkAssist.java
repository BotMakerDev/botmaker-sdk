package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AssistantTool;

import java.util.ArrayList;
import java.util.List;

/**
 * The SDK's tools for the AI assistant that drives Studio: look at what the bot sees ({@link LookTools}), make
 * its pictures and places ({@link PictureTools}) and tag the pictures ({@link TagTools}), shape its activity flow ({@link FlowTools}), set where it
 * looks and how it runs ({@link SetupTools}), and keep the bot's notes on its game and the protocol's steps for
 * where it stands ({@link NotesTools}), and map the game's screens one input at a time ({@link MapTools}).
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

    /** Every tool, by group, each reply ending with how long it took ({@link ToolTiming}). */
    public static final List<AssistantTool<?>> ALL = join(LookTools.TOOLS, ActTools.TOOLS, PictureTools.TOOLS,
            TagTools.TOOLS, FlowTools.TOOLS, SetupTools.TOOLS, NotesTools.TOOLS, MapTools.TOOLS);

    /**
     * How a bot on the SDK is laid out, said to the assistant beside these tools: a bot is its activities, and a
     * flow says which runs next. What the gamebot template's {@code Gamebot} javadoc tells a person, said as rules.
     * It names the {@link Protocol}'s stages only: read_guide gives the steps of the one the bot is at.
     */
    public static final String GUIDE = """
            A bot is a set of activities and a flow that says which runs next. Automating a game goes through
            stages: Setup once; then each screen through Discovery, Build and Trial, in that order; Tuning once
            its trial passed; Upkeep when the game changes. Never write a body for a screen not yet surveyed:
            it would miss the game's own controls.
            - read_guide gives the steps of the stage the bot is at, worked out from its home and popups
              methods and its notes (read_notes, write_notes), and where each screen stands; read_guide with a
              stage gives another's. Read it before starting, and again whenever a stage's check passes.
            - When a stage's check passes for a screen, write_notes that screen's section with its new Stage
              line, and what the stage taught about the game, before going on.
            - Stage 0, Setup: the game in view, a home method and a popups method.
            - Stage 1, Discovery: explore each screen across, from home: cut its marker and every input on it,
              then try_input each one before any screen they open; then survey each screen, its help first.
            - Stage 2, Build: one chore at a time, through the game's own shortcuts, each step proved by a
              picture.
            - Stage 3, Trial: each activity from three states (work to do, nothing to do, interrupted), then
              the whole flow.
            - Stage 4, Tuning: faster, once it is right.
            - Stage 5, Upkeep: a marker or an entrance stops being found.

            The bot's shape
            - One activity per chore (collect the loot, claim the quests, fight the boss). add_activity adds
              its card and its enum in Activities, whose constants are the outcomes it can report (NEXT to
              start with; connect on an outcome it has not got yet adds it). Its work is a public static
              body() in a class of its own, returning that enum. A body does one unit of work, returns, and
              never loops forever: the flow repeats it.
            - The flow (read_flow, connect, set_start, add_flow) wires each outcome to the next activity. Put
              the order of chores there, never in a body calling another body.
            - Navigation lives in one place: the home method takes the game back to the screen every
              activity starts from, safe to call at any moment. Make it with add_method, then name it with
              set_flow_method. An activity that needs another screen opens it from there in its own body.
            - Popups the game opens on its own (an offer, a mail, a level-up) are no body's business: one
              popups method clears them, named with set_flow_method popups, and the run calls it before every
              look at the screen of every activity. A body that works through a popup-shaped screen itself
              switches it off around that step with PopupGuard.enabled(false), and back on after.
            - Pictures, points and regions are the bot's constants (Pictures.CLAIM_RESEARCH): one per idea and
              place, named <idea>_<where>; the same idea on another screen is another picture. Tag each
              picture with the activity that uses it, or a custom tag (declare_tag) for home and popups:
              crop_picture takes tags, list_tags shows them.
            - A run's choices (which flow, which activity is on) are @Param fields, never run properties.
            """;

    private SdkAssist() {}

    @SafeVarargs
    private static List<AssistantTool<?>> join(List<AssistantTool<?>>... groups) {
        List<AssistantTool<?>> all = new ArrayList<>();
        for (List<AssistantTool<?>> group : groups) {
            for (AssistantTool<?> tool : group) all.add(ToolTiming.timed(tool));
        }
        return List.copyOf(all);
    }
}
