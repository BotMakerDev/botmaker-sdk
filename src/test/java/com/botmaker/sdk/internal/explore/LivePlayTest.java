// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.Reach;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a stop carries of the screen it is on: the tree's elements and its readouts' values, read once. */
class LivePlayTest {

    private static final BufferedImage FRAME = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

    /** An app showing {@code tree}, or a game window with no tree when it is null. */
    private record App(UiTree tree) implements Reach {

        @Override
        public boolean readsTrees() {
            return tree != null;
        }

        @Override
        public Optional<ElementMatch.Matcher> treeOn(BufferedImage frame) {
            return Optional.ofNullable(tree).map(ElementMatch::in);
        }

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(FRAME);
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            return Optional.empty();
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {}

        @Override
        public void keys(Combo combo) {}

        @Override
        public void type(String text, boolean clears, boolean submits) {}

        @Override
        public void scroll(Point at, int notches) {}

        @Override
        public void drag(Point from, Point to, Duration time) {}

        @Override
        public long nowMs() {
            return 0;
        }

        @Override
        public void pause(long ms) {}
    }

    private static final UiTree HOME = UiTree.parse("<hierarchy rotation=\"0\">"
            + node("", "", "com.example.app:id/root", false, 0, 0, 1000, 1000).replace("/>", ">")
            + node("1200", "", "com.example.app:id/gold", false, 40, 40, 120, 60)
            + node("", "Settings", "com.example.app:id/settings", true, 900, 40, 980, 120)
            + node("", "", "com.example.app:id/spacer", false, 0, 900, 1000, 1000)
            + "</node></hierarchy>").app("com.example.app");

    private static String node(String text, String desc, String id, boolean clickable, int left, int top, int right,
                               int bottom) {
        return "<node index=\"0\" text=\"" + text + "\" resource-id=\"" + id + "\" class=\"android.widget.TextView\""
                + " package=\"com.example.app\" content-desc=\"" + desc + "\" checkable=\"false\" checked=\"false\""
                + " clickable=\"" + clickable + "\" enabled=\"true\" focusable=\"true\" focused=\"false\""
                + " scrollable=\"false\" long-clickable=\"false\" password=\"false\" selected=\"false\" bounds=\"["
                + left + "," + top + "][" + right + "," + bottom + "]\"/>";
    }

    private static MapScreen home() {
        return new MapScreen(Setup.named("Home"), Map.of(), Map.of("gold", Readout.element(Element.byId("gold"))));
    }

    /** The tree's nodes that read, the clickable one said so, and the stop's screen's readout by its name. */
    @Test
    void aLookCarriesTheScreensElementsAndFacts() {
        ExploreLoop.Shown shown = LivePlay.shown(new App(HOME), Optional.of(home()), FRAME);

        assertEquals(List.of("\"1200\" (40,40 80x20)", "\"Settings\" tappable (900,40 80x80)"), shown.elements());
        assertEquals(Map.of("gold", "1200"), shown.facts());
    }

    /** A stop on a screen the map lacks reads no readout; the tree's elements still come. */
    @Test
    void aStopOffTheMapCarriesTheTreeAlone() {
        ExploreLoop.Shown shown = LivePlay.shown(new App(HOME), Optional.empty(), FRAME);

        assertEquals(List.of("\"1200\" (40,40 80x20)", "\"Settings\" tappable (900,40 80x80)"), shown.elements());
        assertEquals(Map.of(), shown.facts());
    }

    /** A readout the tree finds twice stops the bot when it is read; at a stop it is left out and the rest still read. */
    @Test
    void aReadoutFoundTwiceIsLeftOutNotThrown() {
        UiTree twice = UiTree.parse("<hierarchy rotation=\"0\">"
                + node("", "", "com.example.app:id/root", false, 0, 0, 1000, 1000).replace("/>", ">")
                + node("1200", "", "com.example.app:id/gold", false, 40, 40, 120, 60)
                + node("900", "", "com.example.app:id/gold", false, 40, 80, 120, 100)
                + node("", "Settings", "com.example.app:id/settings", true, 900, 40, 980, 120)
                + "</node></hierarchy>").app("com.example.app");
        MapScreen screen = new MapScreen(Setup.named("Home"), Map.of(), Map.of(
                "gold", Readout.element(Element.byId("gold")),
                "settings", Readout.element(Element.byId("settings"))));

        ExploreLoop.Shown shown = LivePlay.shown(new App(twice), Optional.of(screen), FRAME);

        assertEquals(Map.of("settings", "Settings"), shown.facts());
        assertEquals(3, shown.elements().size());
    }

    /** A game window has no tree: no element, and an element readout is not read, so nothing throws. */
    @Test
    void aWindowWithNoTreeCarriesNoElement() {
        ExploreLoop.Shown shown = LivePlay.shown(new App(null), Optional.of(home()), FRAME);

        assertEquals(ExploreLoop.Shown.NOTHING, shown);
    }
}
