package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Mark;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.marks;
import static com.botmaker.sdk.internal.screens.MapFixtures.picture;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A screen told by its parts that mark: a Mark, an input {@code .marking()}, one {@code .absent()}. */
@SuppressWarnings("removal")
class MarkPartsTest {

    private static final Caption SHOP = Caption.of(Words.of("Shop"), new Zone(0, 0, 400, 80));

    /** Finds what {@code shown} names and reads {@code title} everywhere. */
    private record Seen(Set<String> shown, String title) implements Reach {
        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(FRAME);
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            return shown.contains(picture.id()) ? Optional.of(new Rect(0, 0, 10, 10)) : Optional.empty();
        }

        @Override
        public List<TextResult> read(BufferedImage image) {
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

    private static final BufferedImage FRAME = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);

    private static boolean on(Markers set, Set<String> shown, String title) {
        Seen seen = new Seen(shown, title);
        return Markers.onEach(seen, List.of(set), FRAME).getFirst();
    }

    /** A Mark of each kind and a marking button make the set; an absent Mark keeps it off where that shows. */
    @Test
    void partsMakeTheSet() {
        MapScreen shop = screen("Shop", click("buy").marking())
                .withMark("bag", Mark.of(picture("bag")))
                .withMark("title", Mark.of(SHOP))
                .withMark("noBattle", Mark.of(picture("battle")).absent());
        Markers set = Markers.of(shop);
        assertEquals(Set.of("buy", "bag", "\"Shop\""), set.keys());
        assertEquals(Set.of("battle"), set.absentKeys());
        assertTrue(on(set, Set.of("buy", "bag"), "Shop"));
        assertFalse(on(set, Set.of("buy", "bag", "battle"), "Shop"), "its absent part shows");
        assertFalse(on(set, Set.of("buy", "bag"), "Home"), "its words are not read");
    }

    /** A marking button is found by any of its looks: dimmed, it still tells its screen. */
    @Test
    void aMarkingButtonIsFoundByAnyLook() {
        Button battle = click("battle").looks(List.of(Look.of(picture("battle_dim"), State.DISABLED))).marking();
        Markers set = Markers.of(screen("Home", battle));
        assertEquals(Set.of("battle|battle_dim"), set.keys());
        assertTrue(on(set, Set.of("battle"), ""));
        assertTrue(on(set, Set.of("battle_dim"), ""), "its Disabled look tells the screen too");
        assertFalse(on(set, Set.of(), ""));
    }

    /** A setup's old markers move to parts: the input showing one marks, the rest become Marks; the lists empty. */
    @Test
    void oldMarkersMoveToParts() {
        MapScreen home = screen(Setup.named("Home").markers(marks("battle", "logo")).absent(marks("close_x")),
                click("battle"));
        assertTrue(MarkParts.old(home));
        MapScreen moved = MarkParts.move(home);
        assertFalse(MarkParts.old(moved));
        assertTrue(moved.setup().markers().isEmpty() && moved.setup().absent().isEmpty());
        assertTrue(((Button) moved.inputs().get("battle")).isMarking(), "the button showing it marks");
        assertTrue(Markers.of(home).same(Markers.of(moved)), "the screen is told as before");
    }

    /** An element marker found by its words is named by them, not {@code input}. */
    @Test
    void anElementMarkIsNamedByItsWords() {
        MapScreen policy = screen(Setup.named("Policy").elements(List.of(Element.byText(Words.of("Privacy Policy")))));
        assertEquals(Set.of("privacyPolicy"), MarkParts.move(policy).marks().keySet());
    }

    /** Two screens told by the same parts are said; a part they share, on its own, is not. */
    @Test
    void equalSetsAreSaidASharedPartIsNot() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen("Home", click("frame").marking(), click("gold")));
        screens.put("SHOP", screen("Shop", click("frame").marking(), click("bag")));
        List<String> said = MarkerSets.problems(ScreenGraph.of(screens), null);
        assertEquals(1, said.size(), said.toString());
        assertTrue(said.getFirst().contains("told by the same parts (frame)") && said.getFirst().contains("gold"),
                said.getFirst());

        screens.put("SHOP", screen("Shop", click("frame").marking(), click("bag").marking()));
        screens.put("HOME", screen("Home", click("frame").marking(), click("gold").marking()));
        assertEquals(List.of(), MarkerSets.problems(ScreenGraph.of(screens), null), "frame shared, sets differ");
    }

    /** A set inside another, nothing absent between them, says which part to make absent; one absent part settles it. */
    @Test
    void aSubsetSaysWhichPartToMakeAbsent() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen("Home", click("frame").marking()));
        screens.put("SHOP", screen("Shop", click("frame").marking(), click("bag").marking()));
        List<String> said = MarkerSets.problems(ScreenGraph.of(screens), null);
        assertEquals(1, said.size(), said.toString());
        assertTrue(said.getFirst().startsWith("Home:") && said.getFirst().contains("make bag absent"), said.getFirst());

        screens.put("HOME", screens.get("HOME").withMark("noBag", Mark.of(picture("bag")).absent()));
        assertEquals(List.of(), MarkerSets.problems(ScreenGraph.of(screens), null));
    }
}
