package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A screen's shot is the file its Java names (2026-10-22), never one found by its constant. */
class ScreenShotsTest {

    @Test
    void aShotIsTheFileItsJavaNamesKeptAtTheCapturesSize(@TempDir Path resources) {
        ScreenGraph graph = graph(Map.of("HOME", MapScreen.of(Setup.named("Home").shot("screens/front.png"))));
        assertTrue(ScreenShots.write(resources.resolve("screens/front.png"),
                new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)));

        assertEquals(Optional.of(resources.resolve("screens/front.png")), ScreenShots.file(resources, graph, "HOME"));
        assertEquals(new Size(1920, 1080), ScreenShots.size(ScreenShots.file(resources, graph, "HOME").orElseThrow()),
                "the frame as captured");
        assertTrue(ScreenShots.has(resources, graph, "HOME"));

        ScreenShots.remove(resources, graph.screens().get("HOME"));
        assertFalse(Files.exists(resources.resolve("screens/front.png")));
        assertFalse(ScreenShots.has(resources, graph, "HOME"));
        assertEquals(new Size(), ScreenShots.size(null), "no shot, no size");
    }

    @Test
    void aFileNamedAfterTheConstantIsNoShotWhileTheJavaNamesNone(@TempDir Path resources) {
        ScreenGraph graph = graph(Map.of("HOME", MapScreen.of(Setup.named("Home"))));
        ScreenShots.write(resources.resolve("screens/HOME.png"), new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

        assertEquals(Optional.empty(), ScreenShots.file(resources, graph, "HOME"));
        assertFalse(ScreenShots.has(resources, graph, "HOME"));
    }

    @Test
    void aFirstShotTakesAPathNoOtherScreenNames() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        // Shop was Home once: renamed, it kept Home's file.
        screens.put("SHOP", MapScreen.of(Setup.named("Shop").shot("screens/HOME.png")));
        screens.put("HOME", MapScreen.of(Setup.named("Home")));
        screens.put("EVENT", MapScreen.of(Setup.named("Event").variants(List.of(
                ScreenVariant.named("Halloween 2026").shot("screens/EVENT@halloween-2026.png")))));
        ScreenGraph graph = graph(screens);

        assertEquals("screens/HOME_2.png", ScreenShots.fresh(graph, "HOME", null));
        assertEquals("screens/EVENT.png", ScreenShots.fresh(graph, "EVENT", null));
        assertEquals("screens/EVENT@halloween-2026_2.png", ScreenShots.fresh(graph, "EVENT", "Halloween 2026"));
        assertEquals("screens/EVENT@winter.png", ScreenShots.fresh(graph, "EVENT", "Winter"));
    }

    @Test
    void aLookShowsItsOwnShotElseTheScreens(@TempDir Path resources) {
        ScreenGraph graph = graph(Map.of("EVENT", MapScreen.of(Setup.named("Event").shot("screens/EVENT.png")
                .variants(List.of(ScreenVariant.named("Halloween").shot("screens/EVENT@halloween.png"),
                        ScreenVariant.named("Winter"))))));
        ScreenShots.write(resources.resolve("screens/EVENT.png"), new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB));
        ScreenShots.write(resources.resolve("screens/EVENT@halloween.png"),
                new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB));
        Path look = resources.resolve("screens/EVENT@halloween.png");

        assertEquals(Optional.of(look), ScreenShots.shown(resources, graph, "EVENT", "Halloween"));
        assertEquals(ScreenShots.file(resources, graph, "EVENT"), ScreenShots.shown(resources, graph, "EVENT", "Winter"),
                "a look with no shot of its own shows the screen's");
        assertEquals(ScreenShots.file(resources, graph, "EVENT"), ScreenShots.file(resources, graph, "EVENT", " "));

        ScreenShots.remove(resources, graph.screens().get("EVENT"));
        assertFalse(Files.exists(look), "a screen gone takes its looks' shots along");
    }

    private static ScreenGraph graph(Map<String, MapScreen> screens) {
        return ScreenGraph.of(screens);
    }
}
