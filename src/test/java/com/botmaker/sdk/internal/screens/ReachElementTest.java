package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Reach} finding a bot's elements in the tree read for each frame: synthetic trees, nothing on a device. */
class ReachElementTest {

    private static final UiTree SETTINGS = ElementMatchTest.tree("settings-two-pane.xml", "com.example.settings");
    private static final UiTree DOWNLOADS = ElementMatchTest.tree("downloads-list.xml", "com.example.files");
    private static final UiTree STATES = ElementMatchTest.tree("states.xml", "com.example.app");

    /**
     * An app whose screen is {@code pages}' tree in turn: each scroll shows the next, the last one staying. Every look
     * is a new frame; the tree is read once per frame, as a run reads it. Time is counted, not waited.
     */
    private static final class App implements Reach {
        final List<UiTree> pages;
        final boolean hasTree;
        final FrameTree trees = new FrameTree();
        final List<String> done = new ArrayList<>();
        int page;
        int reads;
        long now;

        App(boolean hasTree, UiTree... pages) {
            this.hasTree = hasTree;
            this.pages = List.of(pages);
        }

        App(UiTree... pages) {
            this(true, pages);
        }

        @Override
        public boolean readsTrees() {
            return hasTree;
        }

        @Override
        public Optional<ElementMatch.Matcher> treeOn(BufferedImage frame) {
            return trees.on(frame, () -> {
                reads++;
                return Optional.of(pages.get(page));
            });
        }

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            return Optional.empty();
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {
            done.add("tap " + at.x() + "," + at.y());
        }

        @Override
        public void keys(Combo combo) {
            done.add("keys");
        }

        @Override
        public void type(String text, boolean clears, boolean submits) {
            done.add("type " + text);
        }

        @Override
        public void scroll(Point at, int notches) {
            done.add("wheel");
        }

        @Override
        public void drag(Point from, Point to, Duration time) {
            done.add("drag " + from.x() + "," + from.y() + " to " + to.x() + "," + to.y());
            page = Math.min(page + 1, pages.size() - 1);
        }

        @Override
        public long nowMs() {
            return now;
        }

        @Override
        public void pause(long ms) {
            now += ms;
        }
    }

    private static BufferedImage frame() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    }

    private static Words words(String text) {
        return Words.of(text);
    }

    @Test
    void elementFoundTappedAtCentre() {
        App app = new App(SETTINGS);
        Element battery = Element.byId("title").text(words("Battery"));
        Point centre = ElementMatch.place(ElementMatch.matches(battery, SETTINGS).getFirst()).center();
        assertTrue(app.perform(Button.of(battery), frame()));
        assertEquals(List.of("tap " + centre.x() + "," + centre.y()), app.done);
    }

    @Test
    void elementMissingIsNotSeen() {
        App app = new App(SETTINGS);
        Button gone = Button.of(Element.byText(words("Bluetooth")));
        assertTrue(app.seen(gone, frame()).isEmpty());
        assertFalse(app.perform(gone, frame()));
        assertEquals(List.of(), app.done);
    }

    @Test
    void twoMatchesStopTheRun() {
        App app = new App(SETTINGS);
        Button network = Button.of(Element.byText(words("Network & internet")).shot("elements/network.png"));
        IllegalStateException stopped = assertThrows(IllegalStateException.class, () -> app.perform(network, frame()));
        assertTrue(stopped.getMessage().startsWith("network: 2 elements match"), stopped.getMessage());
        assertEquals(List.of(), app.done);
    }

    @Test
    void repeatedOrdersCopiesByBounds() {
        App app = new App(DOWNLOADS);
        Button remove = Button.of(Element.byId("remove")).repeated();
        List<Integer> tops = app.copies(remove, null, Order.NORTH, frame()).stream()
                .map(copy -> copy.place().y()).toList();
        assertEquals(List.of(0, 100, 200), tops);
        assertEquals(List.of(200, 100, 0), app.copies(remove, null, Order.SOUTH, frame()).stream()
                .map(copy -> copy.place().y()).toList());
        assertEquals(100, app.seen(remove, null, Pick.nth(2, Order.NORTH), frame()).orElseThrow().place().y());
    }

    @Test
    void disabledReadFromTree() {
        App app = new App(STATES);
        Button save = Button.of(Element.byId("save"));
        assertEquals(State.DISABLED, app.seen(save, frame()).orElseThrow().state());
        assertFalse(app.perform(save, frame()), "a greyed-out input is not pressed");
        assertEquals(List.of(), app.done);
    }

    @Test
    void selectedReadFromTree() {
        App app = new App(STATES);
        assertEquals(State.SELECTED, app.seen(Button.of(Element.byId("tab").text(words("Today"))), frame())
                .orElseThrow().state());
        assertEquals(State.NORMAL, app.seen(Button.of(Element.byId("tab").text(words("Week"))), frame())
                .orElseThrow().state());
        assertEquals(State.SELECTED, app.seen(Button.of(Element.byId("wifi")), frame()).orElseThrow().state(),
                "a switch that is on is picked");
    }

    @Test
    void treeIsReadOncePerFrame() {
        App app = new App(SETTINGS);
        BufferedImage one = frame();
        app.seen(Button.of(Element.byId("title").text(words("Battery"))), one);
        app.seen(Button.of(Element.byId("wifi_switch")), one);
        Markers.onEach(app, List.of(new Markers(List.of(), List.of(), List.of(),
                List.of(Element.byId("list_pane")))), one);
        assertEquals(1, app.reads);
        app.seen(Button.of(Element.byId("wifi_switch")), frame());
        assertEquals(2, app.reads, "another frame, another read");
    }

    @Test
    void noTreeTargetFailsLoudly() {
        App window = new App(false, SETTINGS);
        Button battery = Button.of(Element.byText(words("Battery")).shot("elements/battery.png"));
        IllegalStateException stopped = assertThrows(IllegalStateException.class,
                () -> window.perform(battery, frame()));
        assertEquals("battery: this target shows no tree; an element is found only in an app's", stopped.getMessage());
    }

    @Test
    void elementMarkersMatchOnTree() {
        App app = new App(SETTINGS);
        Markers settings = new Markers(List.of(), List.of(), List.of(),
                List.of(Element.byId("list_pane"), Element.byId("title")));
        Markers other = new Markers(List.of(), List.of(), List.of(), List.of(Element.byId("search_results")));
        assertEquals(List.of(true, false), Markers.onEach(app, List.of(settings, other), frame()));
        assertTrue(settings.any());
        assertFalse(settings.on(picture -> true, words -> true), "elements need the tree, which no predicate reads");
    }

    @Test
    void elementTabIsToldPickedInTree() {
        App app = new App(STATES);
        Markers today = new Markers(List.of(), List.of(), List.of(), List.of(),
                List.of(Element.byId("tab").text(words("Today"))));
        Markers week = new Markers(List.of(), List.of(), List.of(), List.of(),
                List.of(Element.byId("tab").text(words("Week"))));
        assertEquals(List.of(true, false), Markers.onEach(app, List.of(today, week), frame()),
                "both tabs show; only the picked one tells its screen");
        assertTrue(week.any());
    }

    @Test
    void elementSeenInItsLookOfTheTreesState() {
        App app = new App(STATES);
        ImageTemplate on = new ImageTemplate("elements/wifi_on.png");
        Button wifi = Button.of(Element.byId("wifi").shot("elements/wifi.png"))
                .looks(List.of(Look.of(on, State.SELECTED)));
        assertEquals(on, app.seen(wifi, frame()).orElseThrow().picture(), "Pick.until(on) is met");
        Button save = Button.of(Element.byId("save").shot("elements/save.png"))
                .looks(List.of(Look.of(on, State.SELECTED)));
        assertEquals(save.picture(), app.seen(save, frame()).orElseThrow().picture());
    }

    @Test
    void ambiguousListStopsTheRun() {
        App app = new App(STATES);
        Strip strip = Strip.of(Scroll.drag(Order.NORTH, 400, Duration.ofMillis(100)).in(Element.byId("tab")),
                List.of());
        IllegalStateException stopped = assertThrows(IllegalStateException.class,
                () -> app.inView(Button.of(Element.byText(words("Item 9"))), strip, null, frame()));
        assertTrue(stopped.getMessage().contains("2 elements match"), stopped.getMessage());
    }

    @Test
    void elementReadsNodeText() {
        App app = new App(STATES);
        assertEquals(Optional.of("85 %"), app.readout(Readout.element(Element.byId("level")), frame()));
        assertEquals(Optional.of("Profile"), app.readout(Readout.element(Element.byId("avatar")), frame()),
                "an image is read by its label");
        assertEquals(Optional.empty(), app.readout(Readout.element(Element.byId("gone")), frame()));
    }

    @Test
    void stripInElementScrollsUntilShown() {
        UiTree first = list(1, 2, 3, 4, 5);
        UiTree second = list(5, 6, 7, 8, 9);
        App app = new App(first, second);
        Element list = Element.byId("items");
        Button nine = Button.of(Element.byText(words("Item 9")).shot("elements/item9.png"));
        Strip strip = Strip.of(Scroll.drag(Order.NORTH, 400, Duration.ofMillis(100)).in(list),
                List.of(nine.picture()));
        Reach.Seen seen = app.inView(nine, strip, null, frame()).orElseThrow();
        assertEquals(800, seen.place().y());
        assertEquals(List.of("drag 500,500 to 500,100"), app.done, "dragged once, from the list's middle");
        assertTrue(app.inView(Button.of(Element.byText(words("Item 12"))), strip, null, frame()).isEmpty(),
                "a list whose scroll shows nothing new is at its end");
    }

    /** A synthetic list {@code items} of rows, 200 px each from the top, filling a 1000 px list. */
    private static UiTree list(int... items) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < items.length; i++) {
            rows.append(node("Item " + items[i], "", 0, i * 200, 1000, i * 200 + 200));
        }
        return UiTree.parse("<hierarchy rotation=\"0\">" + node("", "com.example.app:id/items", 0, 0, 1000, 1000)
                .replace("/>", ">") + rows + "</node></hierarchy>").app("com.example.app");
    }

    private static String node(String text, String id, int left, int top, int right, int bottom) {
        return "<node index=\"0\" text=\"" + text + "\" resource-id=\"" + id + "\" class=\"android.widget.TextView\""
                + " package=\"com.example.app\" content-desc=\"\" checkable=\"false\" checked=\"false\""
                + " clickable=\"true\" enabled=\"true\" focusable=\"true\" focused=\"false\" scrollable=\"false\""
                + " long-clickable=\"false\" password=\"false\" selected=\"false\" bounds=\"[" + left + "," + top
                + "][" + right + "," + bottom + "]\"/>";
    }
}
