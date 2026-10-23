package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AssistantTool;

import java.util.ArrayList;
import java.util.List;

/**
 * The SDK's tools for the AI assistant that drives Studio: look at what the bot sees ({@link LookTools}), make
 * its pictures and places ({@link PictureTools}) and tag the pictures ({@link TagTools}), shape its activity flow ({@link FlowTools}), set where it
 * looks and how it runs ({@link SetupTools}), and keep the bot's notes on its game and the protocol's steps for
 * where it stands ({@link NotesTools}), and map the game's screens one input at a time in its {@code Screens} and walk
 * them ({@link MapTools}).
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

    /** Every tool, by group, each timed ({@link ToolTiming}). */
    public static final List<AssistantTool<?>> ALL = join(LookTools.TOOLS, ActTools.TOOLS, PictureTools.TOOLS,
            TagTools.TOOLS, FlowTools.TOOLS, SetupTools.TOOLS, NotesTools.TOOLS, MapTools.TOOLS, MapEdits.TOOLS);

    /**
     * How a bot on the SDK is laid out, said to the assistant beside these tools: a bot is its activities, and a
     * flow says which runs next, said as rules.
     * It names the {@link Protocol}'s stages only: read_guide gives the steps of the one the bot is at.
     */
    public static final String GUIDE = """
            A bot is a set of activities and a flow that says which runs next. Automating a game goes through
            stages: Setup once; each screen through Discovery, Build and Trial, in that order; Tuning once its
            trial passed; Upkeep when the game changes. Never write a body for a screen not yet surveyed.
            - read_guide gives the steps of the stage the bot is at, and where each screen stands; read_guide
              with a stage gives another's. Read it before starting and whenever a stage's check passes.
            - The notes are the bot's Java (read_notes, write_notes). When a stage's check passes for a screen,
              write_notes it with reached (its new stage) and what the stage taught.
            - Stage 0, Setup: the game in view and a home method.
            - Stage 1, Discovery: breadth first from home, every screen alike: cut its marker and every input,
              try each, then the next screen read_guide names. crop_pictures and try_input write the bot's
              Screens class, go_to_screen walks it, read_map reads it as 🗺 Screens shows it; edit_map fixes
              what try_input recorded wrong (trying again does not undo it). A list whose entries change by day
              is one screen whose entries come and go.
            - Stage 2, Build: once the map is surveyed, one chore at a time, each body opening with Navigate.to
              its screen and each step proved by a picture.
            - Stage 3, Trial: each activity from three states (work to do, nothing to do, interrupted), then
              the whole flow.
            - Stage 4, Tuning: faster, once it is right.
            - Stage 5, Upkeep: a marker or an entrance stops being found; edit_map moves the map with the game.

            The bot's shape
            - One activity per chore. edit_flow's add_activity adds its card and its enum in Activities, whose
              constants are its outcomes (NEXT first; connect on a new outcome adds it). Its work is a public
              static body() in a class of its own, returning that enum: one unit of work, never a loop
              forever, as the flow repeats it. The order of chores is the flow's (read_flow; edit_flow's
              connect, set_start, add_flow), never a body calling another.
            - A body opens with Navigate.to(Screens.X), returns NOT_NOW (or LOCKED) when that answers false,
              then presses with Navigate.press(Screens.X.field), never picture chains. A stuck step walks back
              to X and runs the body once more, then the run's home.
            - Popups the game opens on its own are screens of the map (crop_pictures its marker with popup
              true, its way out with back true), closed by the run before every look; never a body's business.
              An overlay the map lacks is cleared by its close buttons, then Escape, and the trace says to map
              it. A body working through a popup-shaped screen wraps that step in PopupGuard.enabled(false).
            - Pictures are the bot's constants, one per idea and place, named <idea>_<where> (CLAIM_RESEARCH):
              the same idea on another screen is another picture. Tag each with the activity using it or a
              custom tag (edit_tags, list_tags, edit_pictures).
            - A run's choices (which flow, which activity is on) are @Param fields, never run properties.
            """;

    private SdkAssist() {}

    /** {@link #ALL} and {@code more}, tools of a package this one cannot name (the map's), each timed alike. */
    public static List<AssistantTool<?>> and(List<AssistantTool<?>> more) {
        List<AssistantTool<?>> all = new ArrayList<>(ALL);
        all.addAll(join(more));
        return List.copyOf(all);
    }

    @SafeVarargs
    private static List<AssistantTool<?>> join(List<AssistantTool<?>>... groups) {
        List<AssistantTool<?>> all = new ArrayList<>();
        for (List<AssistantTool<?>> group : groups) {
            for (AssistantTool<?> tool : group) all.add(ToolTiming.timed(tool));
        }
        return List.copyOf(all);
    }
}
