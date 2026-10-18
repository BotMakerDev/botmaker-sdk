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
            entry("SetInput", Hand.now("the picked input's editors: result, information only, comes and goes, "
                    + "never, locked, a button's looks, repeated and cycles, a hotkey's key; ⋯ input")),
            entry("ResetInput", Hand.now("⋯ input ▸ Result ▸ Untried")),
            entry("RemoveInput", Hand.now("⋯ input ▸ Remove from this screen")),
            entry("UseAsBack", Hand.now("⋯ input ▸ Use as the way back")),
            entry("RemoveBack", Hand.now("the panel's Way back ✕")),
            entry("AddWay", Hand.now("⋯ input ▸ Result ▸ Leads to a screen on the map…, the picked input's Leads "
                    + "to…")),
            entry("RemoveWay", Hand.now("the panel's Ways in ⋯ ▸ Remove this way in")),
            entry("WayWhen", Hand.now("an arrow's menu ▸ Open only while")),
            entry("InputWhen", Hand.now("⋯ input ▸ Only there while")),
            entry("Group", Hand.in(6, "the strip and tree inspectors")),
            entry("Link", Hand.in(7, "the tree inspector")),
            entry("LookWhen", Hand.now("the panel's Looks ⋯ ▸ Shows only while")),
            entry("AddMarker", Hand.now("✂ Cut marker, ＋ Marker from a picture… (shows it or never does)")),
            entry("RemoveMarker", Hand.now("the panel's markers ✕")),
            // the map tools
            entry("not_a_screen", Hand.now("⋯ Screen ▸ Not a screen")),
            entry("place_pictures", Hand.now("⋯ Screen ▸ Add pictures the bot has…")),
            entry("try_input", Hand.now("▶, ▶ Try next, and the picked input's ▶ Try: which copy, what it opens, "
                    + "how long, and whether the game stays")),
            entry("go_to_screen", Hand.now("🧭 Go here")),
            entry("edit_map", Hand.now("its actions, each above")),
            // look, act, picture, notes and setup
            entry("which_screen", Hand.now("● Live's line: On <screen>, lit on the graph and in the outline")),
            entry("missed_frames", Hand.now("the board's Missed frames, opened frozen")),
            entry("click", Hand.now("a click on the live board")),
            entry("key", Hand.now("a key on the live board")),
            entry("scroll", Hand.now("the wheel or a drag on the live board")),
            entry("describe", Hand.now("the live board itself")),
            entry("crop_pictures", Hand.now("✂ Cut on the frozen board, then the cut form")),
            entry("edit_pictures", Hand.now("🖼 Manage Pictures; a cut on the board as a new image for a picture")),
            entry("save_region", Hand.now("a cut on the board as an area (Zones)")),
            entry("save_digits", Hand.now("a cut on the board as digits of a number")),
            entry("write_notes", Hand.now("the panel's Notes, Save notes")),
            entry("launch_game", Hand.now("▶ Launch on the board")));

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
