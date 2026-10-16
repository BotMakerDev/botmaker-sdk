package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.value.Ref;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.editors.ScreenEditors.Choice;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A screen in a block: {@code Navigate} is offered, its slot is the screen picker's, and the picker lists the map. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ScreenEditorsTest {

    private static final Executable TO = TestContexts.call((Ref.Of1<GameScreen, ?>) Navigate::to);

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
                if (method.getParameterTypes()[i] != GameScreen.class) continue;
                assertTrue(claimed(TestContexts.slot(method, i, "null")), method.getName() + " is not the picker's");
            }
        }
    }

    /** The map's own screen, the {@code HOME} of {@code Way.of(HOME, …)}, and a row with no call are not. */
    @Test
    void the_maps_own_screen_and_a_row_are_not() {
        Executable way = TestContexts.call((Ref.Of2<GameScreen, Input, Way>) Way::of);
        assertFalse(claimed(TestContexts.slot(way, 0, "HOME")));
        assertFalse(claimed(TestContexts.row(GameScreen.class, "null")));
    }

    @Test
    void the_picker_lists_the_maps_screens_in_order_and_its_popups_only_when_asked() {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").markers(List.of(new ImageTemplate("images/home_map.png"))));
        screens.put("REWARD", GameScreen.named("Reward").markers(List.of(new ImageTemplate("images/reward.png")))
                .popup());
        screens.put("CAMPAIGN", GameScreen.named("Campaign").from(Way.of(BotConstants.screen("HOME"),
                Input.click(new ImageTemplate("images/campaign.png")))));

        ScreenGraph graph = ScreenGraph.of(screens);
        assertEquals(List.of(new Choice("HOME", "Home", "home_map"), new Choice("CAMPAIGN", "Campaign", null)),
                ScreenEditors.choices(graph, false));
        assertEquals(List.of("HOME", "REWARD", "CAMPAIGN"),
                ScreenEditors.choices(graph, true).stream().map(Choice::constant).toList());
    }
}
