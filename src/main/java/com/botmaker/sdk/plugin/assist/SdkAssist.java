package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AssistantTool;

import java.util.ArrayList;
import java.util.List;

/**
 * The SDK's tools for the AI assistant that drives Studio: look at what the bot sees ({@link LookTools}), make
 * its pictures and places ({@link PictureTools}) and tag the pictures ({@link TagTools}), set where it
 * looks and how it runs ({@link SetupTools}), and keep the bot's notes on its game and the protocol's steps for
 * where it stands ({@link NotesTools}), and map the game's screens one input at a time in its {@code Screens} and walk
 * them ({@link MapTools}).
 *
 * <p>Coordinates are the <b>screenshot's</b> pixels: {@code (0, 0)} is the top-left of what {@code screenshot}
 * returned, so the assistant crops what it was shown. Every write goes through the same path as the SDK's own
 * windows, on the FX thread ({@link FxCall}): the picture files and their {@code Pictures} constants, the
 * {@code Points} and {@code Regions} constants, {@code Sdk.captureSource} and the Settings parameters, and this machine's launch target. {@link ActTools}
 * clicks, taps a key or scrolls on the game the look tools see, and says in text what changed; on the user's own
 * desktop only when asked.
 */
public final class SdkAssist {

    /** Every tool, by group, each timed ({@link ToolTiming}). */
    public static final List<AssistantTool<?>> ALL = join(LookTools.TOOLS, ActTools.TOOLS, PictureTools.TOOLS,
            ElementTools.TOOLS, TagTools.TOOLS, SetupTools.TOOLS, NotesTools.TOOLS, MapTools.TOOLS,
            MapEdits.TOOLS, ExploreTools.TOOLS);

    /**
     * How a bot on the SDK is laid out, said to the assistant beside these tools: a bot is its screen map, the
     * actions on its screens, and a plan of goals, said as rules.
     * It names the {@link Protocol}'s stages only: read_guide gives the steps of the one the bot is at.
     */
    public static final String GUIDE = """
            A bot is a map of the game's screens, the actions on them, and a plan of goals that says what
            matters most; the run picks the action that serves the first unmet goal. Automating a game goes through
            stages: Setup once; each screen through Discovery, Build and Trial; Tuning once its
            trial passed; Upkeep when the game changes. Never write an action for a screen not yet surveyed.
            - read_guide gives the current stage's steps and where each screen stands; with a stage, another's.
              Read it before starting and whenever a stage's check passes.
            - The notes are the bot's Java (read_notes, write_notes). When a stage's check passes for a screen,
              write_notes it with reached (its new stage) and what the stage taught.
            - Stage 0, Setup: the game in view and a home method.
            - Stage 1, Discovery: breadth first from home, every screen alike: cut its marker and every input,
              try each, then the next screen read_guide names. crop_pictures and try_input write the bot's
              Screens class, go_to_screen walks it, read_map reads it; edit_map fixes
              what try_input recorded wrong (trying again does not undo it). A list whose entries change by day
              is one screen whose entries come and go.
            - Stage 2, Build: once the map is surveyed, one chore at a time, each step proved by a picture.
            - Stage 3, Trial: each action from three states (work to do, nothing to do, interrupted), then
              the whole plan.
            - Stage 4, Tuning: faster, once it is right.
            - Stage 5, Upkeep: a marker or an entrance stops being found; edit_map moves the map with the game.
            - Keep exploring, once the map has its inputs: run_bot entry "Keep exploring" plays on its own;
              explore wait answers the stop it meets, fix it with the map tools, explore resume, wait again.

            The bot's shape
            - One action per chore: a Do field of its screen (Do.press(input)), saying the goal it serves.
              A goal is a check in Goals.java; a plan is tiers of goals in Plans.java, what matters most
              first. The run walks to the screen of the action that serves the first unmet goal and presses
              it; the order of chores is the plan's, never an action calling another.
            - Work a press cannot say is a Custom input, a method of its own that presses with
              Navigate.press(Screens.X.field), never picture chains.
            - Popups the game opens on its own are screens of the map (crop_pictures its marker with popup
              true, its way out with back true), closed by the run before every look.
              An overlay the map lacks is cleared by its close buttons; the trace says to map it. A walk
              presses no key: map ways out as buttons. A Custom input working through a popup-shaped screen
              wraps that step in PopupGuard.enabled(false).
            - Pictures are the bot's constants, one per idea and place, named <idea>_<where> (CLAIM_RESEARCH).
              Tag each with a tag of its own (edit_tags, list_tags, edit_pictures).
            - A run's choices (which plan) are @Param methods, never run properties.
            - What the game shows that a way or input depends on (a level) is a fact (edit_map add_fact), tested in
              its when (way_when, input_when or look_when: fact Progress::gold, with its test and value). setup
              game_facts lists them.
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
