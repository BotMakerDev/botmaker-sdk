package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.value.Ref;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.MapScreen;
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
