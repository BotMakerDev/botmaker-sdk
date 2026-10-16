package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.value.Ref;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.editors.ScreenEditors.Choice;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A screen in a block: {@code Navigate} is offered, its slot is the screen picker's, and the picker lists the map. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ScreenEditorsTest {

    private static final Executable TO = TestContexts.call((Ref.Of1<Screen, ?>) Navigate::to);

    private static boolean claimed(ValueContext ctx) {
        return SdkEditors.ALL.stream().anyMatch(editor -> editor.matches(ctx));
    }

    @Test
    void navigate_is_offered_in_the_palette() {
        assertTrue(PaletteCatalog.of(Navigate.class).facades().stream()
                .anyMatch(facade -> facade.type() == Navigate.class && facade.offered()));
    }

    @Test
    void a_dropped_or_picked_screen_is_the_pickers() {
        assertTrue(claimed(TestContexts.slot(TO, 0, "null")));
        assertTrue(claimed(TestContexts.slot(TO, 0, "Screens.CAMPAIGN")));
    }

    /** Each screen {@code Navigate} takes is drawn by the picker: what the palette check counts on. */
    @Test
    void every_screen_navigate_takes_is_the_pickers() {
        for (Method method : Navigate.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            for (int i = 0; i < method.getParameterCount(); i++) {
                if (method.getParameterTypes()[i] != Screen.class) continue;
                assertTrue(claimed(TestContexts.slot(method, i, "null")), method.getName() + " is not the picker's");
            }
        }
    }

    /** Each input and pick a press takes has its picker: the input picker, the pick editor. */
    @Test
    void every_input_and_pick_navigate_takes_is_drawn() {
        int seen = 0;
        for (Method method : Navigate.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            for (int i = 0; i < method.getParameterCount(); i++) {
                Class<?> type = method.getParameterTypes()[i];
                if (type != Button.class && type != Hotkey.class && type != Pick.class) continue;
                assertTrue(claimed(TestContexts.slot(method, i, "null")), method.getName() + "'s " + type.getSimpleName());
                seen++;
            }
        }
        assertEquals(5, seen, "press(Button), press(Button, Pick), press(Hotkey), state(Button)");
    }

    /**
     * A button slot is offered a screen's buttons, a strip's members under it and no hotkey; a strip slot only the
     * strips; any input, all of them.
     */
    @Test
    void the_input_picker_offers_only_what_its_slot_takes() {
        Button tavern = Button.of(new ImageTemplate("images/tavern_home.png"));
        Button arena = Button.of(new ImageTemplate("images/arena_home.png"));
        Strip column = Strip.of(Scroll.wheel(-5), List.of(tavern.picture(), arena.picture()));
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("market", Button.of(new ImageTemplate("images/market.png")));
        inputs.put("tavern", tavern);
        inputs.put("quests", Hotkey.of(Key.Q));
        inputs.put("column", column);
        inputs.put("arena", arena);
        MapScreen home = new MapScreen(Setup.named("Home"), inputs);

        List<InputPicker.Choice> buttons = InputPicker.choices(home, Button.class);
        assertEquals(List.of("market", "column", "tavern", "arena"),
                buttons.stream().map(InputPicker.Choice::field).toList());
        assertEquals(List.of(true, false, true, true),
                buttons.stream().map(c -> InputPicker.pickable(c, Button.class)).toList(), "the strip is a heading");
        assertEquals("column", buttons.get(2).strip());
        assertEquals(List.of("column"), InputPicker.choices(home, Strip.class).stream()
                .map(InputPicker.Choice::field).toList());
        assertEquals(List.of("quests"), InputPicker.choices(home, Hotkey.class).stream()
                .map(InputPicker.Choice::field).toList());
        assertEquals(5, InputPicker.choices(home, Input.class).size());
    }

    /** A strip is boxed over the band its members were cut in; a hotkey nowhere. */
    @Test
    void a_strips_box_spans_its_members() {
        Strip column = Strip.of(Scroll.wheel(-5), List.of(new ImageTemplate("images/tavern_home.png"),
                new ImageTemplate("images/arena_home.png")));
        Map<String, FrameCut> cuts = Map.of("tavern_home", FrameCut.of(1280, 720, new Rect(10, 100, 40, 40)),
                "arena_home", FrameCut.of(1280, 720, new Rect(12, 200, 40, 40)));

        assertEquals(Optional.of(new InputPicker.Cut(new Rect(10, 100, 42, 140), 1280)),
                InputPicker.place(column, cuts, null));
        assertEquals(Optional.empty(), InputPicker.place(Hotkey.of(Key.Q), cuts, null));
    }

    /** A row with no call is not: a screen is an argument. */
    @Test
    void a_row_is_not() {
        assertFalse(claimed(TestContexts.row(Screen.class, "null")));
    }

    @Test
    void the_picker_lists_the_maps_screens_in_order_and_its_popups_only_when_asked() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", MapScreen.of(Setup.named("Home").markers(List.of(new ImageTemplate("images/home_map.png")))));
        screens.put("REWARD", MapScreen.of(Setup.named("Reward")
                .markers(List.of(new ImageTemplate("images/reward.png"))).popup()));
        screens.put("CAMPAIGN", MapScreen.of(Setup.named("Campaign").from(BotConstants.input("HOME", "campaign"))));

        ScreenGraph graph = ScreenGraph.of(screens);
        assertEquals(List.of(new Choice("HOME", "Home", "home_map"), new Choice("CAMPAIGN", "Campaign", null)),
                ScreenEditors.choices(graph, false));
        assertEquals(List.of("HOME", "REWARD", "CAMPAIGN"),
                ScreenEditors.choices(graph, true).stream().map(Choice::constant).toList());
    }
}
