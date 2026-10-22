package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.marks;
import static com.botmaker.sdk.internal.screens.MapFixtures.picture;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What tells a screen: its pictures first, its words read only for a screen the pictures leave in. */
class MarkersTest {

    private static final Zone TITLE = new Zone(100, 20, 300, 40);
    private static final Caption SETTINGS = Caption.of(Words.of("Settings"), TITLE);
    private static final Caption PROFILE = Caption.of(Words.of("Profile"), TITLE);

    /** A game showing {@code shown} pictures and, in every zone, the line {@code title}; each read counted. */
    private static final class Game implements Reach {
        final Set<String> shown;
        final String title;
        final List<String> reads = new ArrayList<>();

        Game(Set<String> shown, String title) {
            this.shown = shown;
            this.title = title;
        }

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB));
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            return shown.contains(picture.id()) ? Optional.of(new Rect(0, 0, 10, 10)) : Optional.empty();
        }

        @Override
        public List<TextResult> read(BufferedImage image) {
            reads.add(image.getWidth() + "x" + image.getHeight());
            return List.of(new TextResult(title, new Rect(4, 4, 80, 20), 90f, TextResult.Level.LINE));
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {
        }

        @Override
        public void keys(Combo combo) {
        }

        @Override
        public void type(String text, boolean clears, boolean submits) {
        }

        @Override
        public void scroll(Point at, int notches) {
        }

        @Override
        public void drag(Point from, Point to, Duration time) {
        }

        @Override
        public long nowMs() {
            return 0;
        }

        @Override
        public void pause(long ms) {
        }
    }

    /** A screen its pictures rule out is never read: OCR costs many times a picture's look. */
    @Test
    void picturesThatDecideCallNoOcr() {
        Game game = new Game(Set.of("home_title"), "Settings");
        Markers settings = new Markers(marks("menu_frame"), List.of(), List.of(SETTINGS));
        Markers home = new Markers(marks("home_title"), List.of());
        assertEquals(List.of(false, true), Markers.onEach(game, List.of(settings, home), game.look().orElseThrow()));
        assertEquals(List.of(), game.reads, "the menu's frame is not there, so its title is never read");
    }

    /**
     * Two screens of one frame are told apart by the title each reads, read once for both and only in its zone; a
     * screen of words alone is told by them.
     */
    @Test
    void aTextMarkerTellsTwoScreensOfOnePicture() {
        Game game = new Game(Set.of("menu_frame"), " SETTINGS: ");
        Markers settings = new Markers(marks("menu_frame"), List.of(), List.of(SETTINGS));
        Markers profile = new Markers(marks("menu_frame"), List.of(), List.of(PROFILE));
        BufferedImage frame = game.look().orElseThrow();
        assertEquals(List.of(true, false), Markers.onEach(game, List.of(settings, profile), frame));
        assertEquals(List.of("300x40", "300x40"), game.reads, "each caption read once, in its zone");
        game.reads.clear();
        assertEquals(List.of(true, true), Markers.onEach(game,
                List.of(new Markers(List.of(), List.of(), List.of(SETTINGS)), settings), frame));
        assertEquals(List.of("300x40"), game.reads, "words two screens name are read once");
    }

    /** A tab needs no marker: its button picked tells it, through the reference its way in names it by. */
    @Test
    void aTabIsToldByItsSelectedButton() {
        Button gearTab = click("gear_tab").looks(List.of(Look.of(picture("gear_tab_on"), State.SELECTED)));
        Button skillsTab = click("skills_tab").looks(List.of(Look.of(picture("skills_tab_on"), State.SELECTED),
                Look.of(picture("skills_tab_new"), State.BADGE)));
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HERO", screen(Setup.named("Hero").markers(marks("hero_title")), gearTab, skillsTab,
                click("info")));
        screens.put("GEAR", screen(Setup.named("Gear").from(ref("HERO", "gear_tab")).tab()));
        screens.put("SKILLS", screen(Setup.named("Skills").from(ref("HERO", "skills_tab")).tab()));
        screens.put("INFO", screen(Setup.named("Info").from(ref("HERO", "info")).tab()));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals("gear_tab_on", graph.markers("GEAR").ids());
        assertEquals("skills_tab_on", graph.markers("SKILLS").ids(), "only its Selected look");
        assertFalse(graph.markers("INFO").any(), "a button with no Selected look tells nothing");
        assertEquals("gear_tab_on", Markers.of(MapScreen.of(Setup.named("Gear").from(gearTab).tab())).ids(),
                "a run's own button, no reference");

        Game game = new Game(Set.of("hero_title", "gear_tab_on", "skills_tab"), "");
        BufferedImage frame = game.look().orElseThrow();
        assertEquals(List.of(true, false), Markers.onEach(game,
                List.of(graph.markers("GEAR"), graph.markers("SKILLS")), frame));
        assertEquals(Optional.of("HERO"), graph.tabParent("GEAR"));
        assertTrue(graph.tabOf("GEAR", "HERO"));
    }
}
