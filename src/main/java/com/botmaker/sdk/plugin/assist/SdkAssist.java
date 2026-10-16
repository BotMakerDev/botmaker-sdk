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
     * flow says which runs next. What the gamebot template's {@code Gamebot} javadoc tells a person, said as rules.
     * It names the {@link Protocol}'s stages only: read_guide gives the steps of the one the bot is at.
     */
    public static final String GUIDE = """
            A bot is a set of activities and a flow that says which runs next. Automating a game goes through
            stages: Setup once; then each screen through Discovery, Build and Trial, in that order; Tuning once
            its trial passed; Upkeep when the game changes. Never write a body for a screen not yet surveyed:
            it would miss the game's own controls.
            - read_guide gives the steps of the stage the bot is at, worked out from its home method and its
              Screens, and where each screen stands; read_guide with a stage gives another's.
              Read it before starting, and again whenever a stage's check passes.
            - The notes are the bot's Java (read_notes, write_notes): each screen's on its Screens constant, what
              holds across the game on the run. When a stage's check passes for a screen, write_notes it with
              reached (its new stage) and what the stage taught about the game, before going on.
            - Stage 0, Setup: the game in view and a home method.
            - Stage 1, Discovery: every screen the same way, from home: cut its marker and every input on it,
              try each (every list scrolled to both ends), and finish a screen an input opens before the next
              input. crop_pictures and try_input write the map, the bot's Screens class; go_to_screen walks it;
              read_map reads it as the user's 🗺 Screens window shows it, and what it cannot do. A button that
              changes its look is one input (a box's variantOf + state); the same screen dressed otherwise
              (an event's theme) is one of its looks (screenLook), never a screen of its own; a list whose
              entries change by day is one screen whose entries come and go. A tab whose content has a title
              and inputs of its own is a screen of its own, the tab its way in. A picture sits at one place
              on every screen that uses it: where a screen shows it elsewhere, the tools cut that screen a
              copy of its own. When a method of the bot can say
              when one is there, name it as a box's when (Firestone::eventOn). What try_input recorded
              wrong (a panel taken for a way back, a result, a way in) is fixed with edit_map: trying the
              input again does not undo it.
            - Stage 2, Build: one chore at a time, through the game's own shortcuts, each step proved by a
              picture.
            - Stage 3, Trial: each activity from three states (work to do, nothing to do, interrupted), then
              the whole flow.
            - Stage 4, Tuning: faster, once it is right.
            - Stage 5, Upkeep: a marker or an entrance stops being found; edit_map moves the map with the game.

            The bot's shape
            - One activity per chore (collect the loot, claim the quests, fight the boss). edit_flow's
              add_activity adds its card and its enum in Activities, whose constants are the outcomes it can
              report (NEXT to start with; connect on an outcome it has not got yet adds it). Its work is a
              public static body() in a class of its own, returning that enum. A body does one unit of work,
              returns, and never loops forever: the flow repeats it.
            - The flow (read_flow; edit_flow's connect, set_start, add_flow) wires each outcome to the next
              activity. Put the order of chores there, never in a body calling another body.
            - Navigation lives in one place: the home method takes the game back to the screen every
              activity starts from, safe to call at any moment. Make it with add_method, then name it with
              edit_flow's set_flow_method. An activity that needs another screen opens it from there in its
              own body.
            - Popups the game opens on its own (an offer, a mail, a level-up) are no body's business: each is a
              screen of the map (crop_pictures its marker with popup true, its way out with back true), and the
              run closes the ones it shows before every look at the screen of every activity. An overlay the
              map lacks is cleared by the map's close buttons, then Escape, and the run's trace says to map
              it. A body that works through a popup-shaped screen itself switches the closing off around that
              step with PopupGuard.enabled(false), and back on after.
            - Pictures, points and regions are the bot's constants (Pictures.CLAIM_RESEARCH): one per idea and
              place, named <idea>_<where>; the same idea on another screen is another picture. Tag each
              picture with the activity that uses it, or a custom tag (edit_tags) for home and popups:
              crop_pictures takes tags, list_tags shows them, edit_pictures changes them.
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
