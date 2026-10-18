package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AssistantTool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every map action an assistant's tool can do has a hand path in 🗺 Screens (the user's call, 2026-10-18): each
 * edit_map action, each map tool, and the look, act, picture, notes and setup tools a person needs to build the map.
 * A new action fails here until it is given one; an action not yet covered names the phase of the screens-by-hand
 * plan that covers it, and once that plan ends none is left.
 */
class HandParityTest {

    /** Where a person does an action by hand; {@code phase} 0 when they can now, else the phase that adds it. */
    private record Hand(String where, int phase) {
        static Hand now(String where) {
            return new Hand(where, 0);
        }

        static Hand in(int phase, String where) {
            return new Hand(where, phase);
        }
    }

    /** The tools of the look, act, picture, notes and setup lists that act on the map or reach a screen. */
    private static final Set<String> MAP_SIDE = Set.of("which_screen", "missed_frames", "click", "key", "scroll",
            "describe", "crop_pictures", "edit_pictures", "save_region", "save_digits", "write_notes", "launch_game");

    private static final Map<String, Hand> HANDS = Map.ofEntries(
            // edit_map's actions
            entry("Rename", Hand.now("⋯ Screen ▸ Rename…")),
            entry("Delete", Hand.now("⋯ Screen ▸ Delete…")),
            entry("Popup", Hand.now("⋯ Screen ▸ Popup")),
            entry("NewScreen", Hand.now("⋯ input ▸ Result ▸ Opens a new screen…, ⋯ Screen ▸ New screen from here…")),
            entry("SetInput", Hand.in(5, "⋯ input: result, information only, comes and goes, never; locked and "
                    + "repeated in the button inspector")),
            entry("ResetInput", Hand.now("⋯ input ▸ Result ▸ Untried")),
            entry("RemoveInput", Hand.now("⋯ input ▸ Remove from this screen")),
            entry("UseAsBack", Hand.now("⋯ input ▸ Use as the way back")),
            entry("RemoveBack", Hand.in(2, "the screen inspector's way back")),
            entry("RemoveWay", Hand.in(2, "the screen inspector's ways in")),
            entry("WayWhen", Hand.now("an arrow's menu ▸ Open only while")),
            entry("InputWhen", Hand.now("⋯ input ▸ Only there while")),
            entry("Group", Hand.in(6, "the strip and tree inspectors")),
            entry("Link", Hand.in(7, "the tree inspector")),
            entry("LookWhen", Hand.in(2, "the screen inspector's looks")),
            entry("AddMarker", Hand.in(2, "✂ Cut marker; absent ones in the screen inspector's markers")),
            entry("RemoveMarker", Hand.now("the panel's markers ✕")),
            // the map tools
            entry("not_a_screen", Hand.now("⋯ Screen ▸ Not a screen")),
            entry("place_pictures", Hand.now("⋯ Screen ▸ Add pictures the bot has…")),
            entry("try_input", Hand.in(5, "▶ and ▶ Try next; which copy, what it opens, settle and stay on the board")),
            entry("go_to_screen", Hand.now("🧭 Go here")),
            entry("edit_map", Hand.now("its actions, each above")),
            // look, act, picture, notes and setup
            entry("which_screen", Hand.in(3, "the live board's screen label")),
            entry("missed_frames", Hand.in(3, "the board's missed frames list")),
            entry("click", Hand.in(3, "a click on the live board")),
            entry("key", Hand.in(3, "a key on the live board")),
            entry("scroll", Hand.in(3, "the wheel or a drag on the live board")),
            entry("describe", Hand.in(3, "the live board itself")),
            entry("crop_pictures", Hand.in(4, "✂ on the frozen board, with the cut form")),
            entry("edit_pictures", Hand.in(4, "🖼 Manage Pictures; recut on the board")),
            entry("save_region", Hand.in(4, "a zone cut on the board")),
            entry("save_digits", Hand.in(4, "a digits cut on the board")),
            entry("write_notes", Hand.in(2, "the screen inspector's notes")),
            entry("launch_game", Hand.in(3, "▶ Launch on the board")));

    /** Every action the parity covers: edit_map's actions by their record, then the tools by name. */
    private static Set<String> actions() {
        Set<String> actions = new TreeSet<>();
        Arrays.stream(MapEdits.Edit.class.getPermittedSubclasses()).map(Class::getSimpleName).forEach(actions::add);
        Stream.concat(MapTools.TOOLS.stream(), MapEdits.TOOLS.stream()).map(AssistantTool::name).forEach(actions::add);
        actions.addAll(MAP_SIDE);
        return actions;
    }

    @Test
    void everyMapActionHasAHandPath() {
        List<String> missing = new ArrayList<>(actions());
        missing.removeAll(HANDS.keySet());
        assertEquals(List.of(), missing, "give each a hand path in 🗺 Screens, or the phase that adds one");
    }

    @Test
    void theTableNamesNoActionThatIsGone() {
        Set<String> stale = new TreeSet<>(HANDS.keySet());
        stale.removeAll(actions());
        assertEquals(Set.of(), stale);
    }

    @Test
    void theMapSideToolsAreStillTools() {
        Set<String> tools = Stream.of(LookTools.TOOLS, ActTools.TOOLS, PictureTools.TOOLS, NotesTools.TOOLS,
                SetupTools.TOOLS).flatMap(List::stream).map(AssistantTool::name).collect(Collectors.toSet());
        for (String name : MAP_SIDE) assertTrue(tools.contains(name), name);
    }
}
