package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A member of a strip: found anywhere along it, and scrolled to over a sibling while it is not there; the copies of a
 * repeated button, picked along an order.
 */
class ReachTest {

    /** A picture cut at {@code slot} of the column: 100 px apart down from y 100. */
    private static ImageTemplate member(String id, int slot) {
        return new ImageTemplate("images/" + id + ".png").at(new Rect(10, 100 + 100 * slot, 80, 80));
    }

    private static final ImageTemplate TAVERN = member("tavern", 0);
    private static final ImageTemplate ARENA = member("arena", 1);
    private static final ImageTemplate CAMPAIGN = member("campaign", 2);
    private static final ImageTemplate MACHINES = member("war_machines", 5);
    private static final Strip COLUMN = Strip.of(Scroll.wheel(-1), List.of(TAVERN, ARENA, CAMPAIGN, MACHINES));

    /**
     * A column of icons, six slots in view: each icon at its slot less how far the column is scrolled, a notch down
     * scrolling it one slot, never past its ends. Time is counted, not waited.
     */
    private static class Column implements Reach {
        final Map<String, Integer> slots;
        final int end;
        final List<String> done = new ArrayList<>();
        final List<Point> wheel = new ArrayList<>();
        int scrolled;
        long now;

        Column(Map<String, Integer> slots, int end) {
            this.slots = slots;
            this.end = end;
        }

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            Integer slot = slots.get(picture.id());
            if (slot == null || slot - scrolled < 0 || slot - scrolled > 5) return Optional.empty();
            return Optional.of(new Rect(10, 100 + 100 * (slot - scrolled) + bounce, 80, 80));
        }

        /** How far the icons sit off their slots: a column that bounces at its end. */
        int bounce;

        /** A drag up by a slot's height scrolls it one slot on, as a notch down does. */
        @Override
        public void drag(Point from, Point to, Duration time) {
            done.add("drag " + (to.y() - from.y()));
            wheel.add(from);
            scrolled = Math.clamp(scrolled - (to.y() - from.y()) / 100, 0, end);
        }

        /** A click as {@code click <y>}, another gesture by its id, a hold with its time. */
        @Override
        public void press(Point at, Gesture gesture, Duration hold) {
            done.add((gesture == Gesture.CLICK ? "click" : gesture.id()) + " " + at.y()
                    + (gesture == Gesture.HOLD ? " " + hold.toMillis() : ""));
        }

        /** Keys together as {@code key CTRL+S}, a hold with its time. */
        @Override
        public void keys(Combo combo) {
            done.add("key " + combo.keys().stream().map(Key::name).collect(Collectors.joining("+"))
                    + (combo.hold().isZero() ? "" : " " + combo.hold().toMillis()));
        }

        /** Typing as {@code type "text"}, after {@code clear} and before {@code enter} when they are asked. */
        @Override
        public void type(String text, boolean clears, boolean submits) {
            if (clears) done.add("clear");
            done.add("type \"" + text + "\"");
            if (submits) done.add("enter");
        }

        @Override
        public void scroll(Point at, int notches) {
            done.add("scroll " + notches);
            wheel.add(at);
            scrolled = Math.clamp(scrolled - notches, 0, end);
        }

        @Override
        public long nowMs() {
            return now;
        }

        @Override
        public void pause(long ms) {
            now += ms;
        }

        boolean press(ImageTemplate picture, Strip strip) {
            return perform(Button.of(picture), strip, null, look().orElseThrow());
        }
    }

    @Test
    void aMemberIsFoundAtAnotherSlotOfItsStrip() {
        // Cut at the third slot; an icon above it came, so it shows at the fourth.
        Column column = new Column(Map.of("tavern", 0, "campaign", 3), 0);
        assertTrue(column.press(CAMPAIGN, COLUMN));
        assertEquals(List.of("click 440"), column.done);
    }

    @Test
    void aMemberOutOfViewIsScrolledToOverASiblingThatShows() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 8), 4);
        assertTrue(column.press(MACHINES, COLUMN));
        // Slot 8 is the sixth in view after three notches down: 100 + 100 * 5, centred.
        assertEquals(List.of("scroll -1", "scroll -1", "scroll -1", "click 640"), column.done);
        assertTrue(column.wheel.stream().allMatch(at -> at.x() == 50), "the wheel turns over a member");
        assertEquals(3 * 2 * Reach.SCROLL_SETTLE_MS, column.now, "each scroll is looked at until it holds still");
    }

    /** A column that glides on for a while after each notch is judged once it stops, never mid-way (Firestone). */
    @Test
    void aStripIsJudgedOnceItHoldsStill() {
        Column gliding = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 8), 4) {
            int owed;

            @Override
            public void scroll(Point at, int notches) {
                done.add("scroll " + notches);
                owed -= notches;
            }

            @Override
            public void pause(long ms) {
                super.pause(ms);
                // Each look after the first moves it one slot more, until it has moved as far as it was scrolled.
                if (owed != 0) {
                    int step = Integer.signum(owed);
                    int next = Math.clamp(scrolled + step, 0, end);
                    owed = next == scrolled ? 0 : owed - step;
                    scrolled = next;
                }
            }
        };
        gliding.scrolled = 4;
        assertTrue(gliding.press(TAVERN, COLUMN.scroll(Scroll.wheel(-3))), gliding.done.toString());
        assertEquals(List.of("scroll -3", "scroll 3", "scroll 3", "click 140"), gliding.done);
    }

    /** A column that starts moving only a second after the wheel turns is not taken for one at its end. */
    @Test
    void aStripThatMovesLateIsNotTakenForItsEnd() {
        Column late = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 8), 4) {
            int owed;
            long at;

            @Override
            public void scroll(Point at, int notches) {
                done.add("scroll " + notches);
                owed -= notches;
                this.at = now;
            }

            @Override
            public void pause(long ms) {
                super.pause(ms);
                if (owed != 0 && now - at > 1000) {
                    scrolled = Math.clamp(scrolled + owed, 0, end);
                    owed = 0;
                }
            }
        };
        late.scrolled = 2;
        assertTrue(late.press(TAVERN, COLUMN), late.done.toString());
        // Down to the end (two slots), then up four to the top: no scroll is judged before the column moved.
        assertEquals(List.of("scroll -1", "scroll -1", "scroll -1", "scroll 1", "scroll 1", "scroll 1", "scroll 1",
                "click 140"), late.done);
    }

    /** Scrolled to its end before the run looked: no member moved, so the scroll turns back up (Home's column). */
    @Test
    void aStripAtItsEndIsScrolledTheOtherWay() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 8), 4);
        column.scrolled = 4;
        assertTrue(column.press(TAVERN, COLUMN));
        assertEquals(List.of("scroll -1", "scroll 1", "scroll 1", "scroll 1", "scroll 1", "click 140"),
                column.done);
    }

    /** Scrolled to an end of icons the strip does not name: no member shows, so the wheel turns over its band. */
    @Test
    void aStripShowingNoMemberIsScrolledOverItsBand() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2), 8);
        column.scrolled = 8;
        assertTrue(column.press(TAVERN, COLUMN), column.done.toString());
        // Three blind scrolls down turn it back; eight up bring the tavern.
        assertEquals(Reach.BLIND_SCROLLS + 8 + 1, column.done.size(), column.done.toString());
        assertEquals(new Point(50, 390), column.wheel.getFirst(), "the middle of the band the members were cut in");
        assertEquals("click 140", column.done.getLast());
    }

    @Test
    void aMemberNeitherWayBringsIsGivenUpOn() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2), 4);
        assertFalse(column.press(MACHINES, COLUMN));
        // Down all its scrolls: past every member no end shows. Then up: four and the one that met the top.
        assertEquals(Scroll.MOST + 5, column.done.size(), column.done.toString());
        assertTrue(column.done.stream().allMatch(d -> d.startsWith("scroll")));

        // Past every member, the wheel stays where the last one showed; a strip with no end below is given up on:
        // all its scrolls down, as many back up, and the one that met the top.
        Column endless = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2), 100);
        assertFalse(endless.press(MACHINES, COLUMN));
        assertEquals(2 * Scroll.MOST + 1, endless.done.size(), endless.done.toString());
    }

    /** The way back searches as far past the start as the first way went: back to the start, then on past it. */
    @Test
    void theWayBackGoesPastWhereTheSearchBegan() {
        ImageTemplate far = member("far", 5);
        Strip strip = Strip.of(Scroll.wheel(-1), List.of(TAVERN, ARENA, CAMPAIGN, far));
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "far", 8), 100);
        column.scrolled = 6;
        assertTrue(column.press(TAVERN, strip), column.done.toString());
        // Eight down, no end met; then fourteen up, from 14 to 0, eight of them past the start.
        assertEquals(Scroll.MOST + 14 + 1, column.done.size());
        assertEquals("click 140", column.done.getLast());
    }

    /** A strip measured fast is looked at sooner after each scroll: a sixth of its settle, from 100 ms. */
    @Test
    void aFastStripIsLookedAtSooner() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 8), 4);
        Strip fast = COLUMN.scroll(Scroll.wheel(-1).settle(Duration.ofMillis(300)));
        assertTrue(column.press(MACHINES, fast));
        assertEquals(List.of("scroll -1", "scroll -1", "scroll -1", "click 640"), column.done);
        assertEquals(3 * 2 * Reach.MEASURE_MS, column.now);
    }

    /**
     * A strip measured slow is let start late: one that moves only 3.5 s after the wheel is taken for at its end
     * at the SDK's 3 s, and found at its own 5 s.
     */
    @Test
    void aSlowStripIsWaitedForByItsOwnSettle() {
        class Late extends Column {
            int owed;
            long at;

            Late() {
                super(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 8), 4);
            }

            @Override
            public void scroll(Point at, int notches) {
                done.add("scroll " + notches);
                owed -= notches;
                this.at = now;
            }

            @Override
            public void pause(long ms) {
                super.pause(ms);
                if (owed != 0 && now - at > 3500) {
                    scrolled = Math.clamp(scrolled + owed, 0, end);
                    owed = 0;
                }
            }
        }
        Late slow = new Late();
        assertTrue(slow.press(MACHINES, COLUMN.scroll(Scroll.wheel(-1).settle(Duration.ofSeconds(5)))),
                slow.done.toString());
        assertEquals(List.of("scroll -1", "scroll -1", "scroll -1", "click 640"), slow.done);

        Late hurried = new Late();
        hurried.press(MACHINES, COLUMN);
        assertEquals("scroll 1", hurried.done.get(1), "judged at its end before it moved: turned the other way");
    }

    /** A drag strip is dragged from a member the way its members go, and the other way back. */
    @Test
    void aDragStripIsDraggedFromAMember() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 8), 4);
        Strip dragged = COLUMN.scroll(Scroll.drag(Order.NORTH, 100, Duration.ofMillis(300)));
        assertTrue(column.press(MACHINES, dragged));
        assertEquals(List.of("drag -100", "drag -100", "drag -100", "click 640"), column.done);
        assertTrue(column.wheel.stream().allMatch(at -> at.x() == 50), "dragged from a member");

        column.done.clear();
        assertTrue(column.press(TAVERN, dragged));
        // One drag on to its end, one that met it, then back four to the top.
        assertEquals(List.of("drag -100", "drag -100", "drag 100", "drag 100", "drag 100", "drag 100", "click 140"),
                column.done);
    }

    /** At its end a column bouncing 10 px is at its end by its step; by the SDK's 4 px it seems to move on. */
    @Test
    void anEndIsJudgedByTheStripsStep() {
        class Bouncing extends Column {
            Bouncing() {
                super(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 8), 4);
                scrolled = 4;
            }

            @Override
            public void scroll(Point at, int notches) {
                int was = scrolled;
                super.scroll(at, notches);
                bounce = scrolled == was ? 10 - bounce : 0;
            }
        }
        Bouncing measured = new Bouncing();
        assertTrue(measured.press(TAVERN, COLUMN.scroll(Scroll.wheel(-1).step(100))), measured.done.toString());
        assertEquals(List.of("scroll -1", "scroll 1", "scroll 1", "scroll 1", "scroll 1", "click 140"),
                measured.done);

        Bouncing unmeasured = new Bouncing();
        assertTrue(unmeasured.press(TAVERN, COLUMN));
        assertEquals(Scroll.MOST + 4 + 1, unmeasured.done.size(), "every scroll down seemed to move it");
    }

    /** Discovery scrolls a strip once and times it: how far its members went, how long they glided. */
    @Test
    void aStripIsMeasured() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 5), 4);
        BufferedImage frame = column.look().orElseThrow();
        // At once: one slot, held still at the first look. 100 ms, half again plus 200, rounded up.
        assertEquals(Optional.of(Scroll.wheel(-1).step(100).settle(Duration.ofMillis(400))),
                column.measure(COLUMN, frame));

        Column gliding = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 5), 4) {
            int owed;

            @Override
            public void scroll(Point at, int notches) {
                owed -= notches;
            }

            @Override
            public void pause(long ms) {
                super.pause(ms);
                if (owed != 0) {
                    scrolled++;
                    owed--;
                }
            }
        };
        // Two slots, one at each look: still from 200 ms; the campaign and the war machines moved 200 px.
        assertEquals(Optional.of(Scroll.wheel(-2).step(200).settle(Duration.ofMillis(500))),
                gliding.measure(COLUMN.scroll(Scroll.wheel(-2)), frame));

        Column atEnd = new Column(Map.of("tavern", 0, "arena", 1), 0);
        assertEquals(Optional.empty(), atEnd.measure(COLUMN, frame), "nothing moved: no measure");
    }

    /** A scroll that carries every member shown out of view tells nothing of how far it goes: no measure. */
    @Test
    void aStripWhoseMembersAllLeaveTheViewIsNotMeasured() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1), 10);
        BufferedImage frame = column.look().orElseThrow();

        assertEquals(Optional.empty(), column.measure(COLUMN.scroll(Scroll.wheel(-8)), frame),
                "measured 0 px would read as a strip at its end");
    }

    @Test
    void aStripOfNoNotchesScrollsNothing() {
        Column column = new Column(Map.of("tavern", 0, "war_machines", 8), 4);
        assertFalse(column.press(MACHINES, COLUMN.scroll(Scroll.wheel(0))));
        assertEquals(List.of(), column.done);
    }

    /** A strip tried as an input scrolls once over a member that shows; a hotkey is pressed. */
    @Test
    void aStripScrollsOnceOverAMemberAndAHotkeyIsPressed() {
        Column column = new Column(Map.of("arena", 1), 4);
        assertTrue(column.perform(COLUMN, column.look().orElseThrow()));
        assertEquals(List.of("scroll -1"), column.done);
        assertEquals(new Point(50, 240), column.wheel.getFirst());
        assertTrue(column.perform(Hotkey.of(Key.ESCAPE), column.look().orElseThrow()));
        assertEquals("key ESCAPE", column.done.getLast());
    }

    /** A button whose gesture is a double click is double-clicked at the centre of where it was found. */
    @Test
    void aDoubleButtonIsDoubleClickedAtItsCentre() {
        Column column = new Column(Map.of("arena", 1), 4);
        assertTrue(column.perform(Button.of(ARENA).gesture(Gesture.DOUBLE), column.look().orElseThrow()));
        assertEquals(List.of("double 240"), column.done);
    }

    /** A held button is held its own time, or the default time when it has none. */
    @Test
    void aHoldButtonIsHeldItsDuration() {
        Column column = new Column(Map.of("arena", 1), 4);
        assertTrue(column.perform(Button.of(ARENA).hold(Duration.ofSeconds(2)), column.look().orElseThrow()));
        assertTrue(column.perform(Button.of(ARENA).gesture(Gesture.HOLD), column.look().orElseThrow()));
        assertEquals(List.of("hold 240 2000", "hold 240 " + Button.HOLD_TIME.toMillis()), column.done);
        assertEquals(Button.of(ARENA), Button.of(ARENA).hold(Duration.ofSeconds(2)).gesture(Gesture.CLICK),
                "a click keeps no hold time");
    }

    /** A hotkey of keys together presses them together, held as its combo says. */
    @Test
    void aComboHotkeyPressesItsKeysTogether() {
        Column column = new Column(Map.of(), 4);
        assertTrue(column.perform(Hotkey.of(Combo.of(Key.CTRL, Key.S)), column.look().orElseThrow()));
        assertTrue(column.perform(Hotkey.of(Combo.of(Key.W).held(Duration.ofMillis(500))),
                column.look().orElseThrow()));
        assertEquals(List.of("key CTRL+S", "key W 500"), column.done);
    }

    /**
     * A text field is clicked, given time to take the click, cleared, typed and submitted; one whose body gives its
     * text is only clicked here, where nothing hands it one.
     */
    @Test
    void aFieldIsClickedClearedTypedAndSubmitted() {
        Column column = new Column(Map.of("arena", 1), 4);
        assertTrue(column.perform(Button.of(ARENA).types(Words.of("Dragons")).clears().submits(),
                column.look().orElseThrow()));
        assertEquals(List.of("click 240", "clear", "type \"Dragons\"", "enter"), column.done);
        assertEquals(Reach.FIELD_FOCUS_MS, column.now, "the field took the click before the typing");
        column.done.clear();
        assertTrue(column.perform(Button.of(ARENA).types(Words.of("x")), column.look().orElseThrow()));
        assertTrue(column.perform(Button.of(ARENA).typed().submits(), column.look().orElseThrow()));
        assertEquals(List.of("click 240", "type \"x\"", "click 240"), column.done);
    }

    /** A column whose drags are told by where they go: {@code drag 50,240 to 50,340 in 400}. */
    private static final class Dropping extends Column {
        Dropping(Map<String, Integer> slots) {
            super(slots, 0);
        }

        @Override
        public void drag(Point from, Point to, Duration time) {
            done.add("drag " + from.x() + "," + from.y() + " to " + to.x() + "," + to.y() + " in " + time.toMillis());
        }
    }

    /**
     * A drag is pressed on its button and let go on its picture, found on the same frame, or at its zone's centre, in
     * its time; nothing is clicked.
     */
    @Test
    void aDragGoesFromItsPictureToItsTarget() {
        Dropping column = new Dropping(Map.of("arena", 1, "campaign", 2));
        BufferedImage frame = column.look().orElseThrow();
        assertTrue(column.perform(Button.of(ARENA).dragsOnto(CAMPAIGN), frame));
        assertTrue(column.perform(Button.of(ARENA).dragsInto(new Zone(400, 300, 100, 50))
                .dragTime(Duration.ofMillis(900)), frame));
        assertEquals(List.of("drag 50,240 to 50,340 in 400", "drag 50,240 to 450,325 in 900"), column.done);
    }

    /** A drag whose picture is not on the frame is no press: nothing is dragged or clicked. */
    @Test
    void aDragWhoseTargetIsMissingDoesNothing() {
        Dropping column = new Dropping(Map.of("arena", 1));
        assertFalse(column.perform(Button.of(ARENA).dragsOnto(CAMPAIGN), column.look().orElseThrow()));
        assertEquals(List.of(), column.done);
        assertTrue(column.dropOn(Button.of(ARENA), column.look().orElseThrow()).isEmpty(), "a button drags nowhere");
    }

    /** Three Go buttons on a grid of mission cards: two on the top row, one below, found out of reading order. */
    private static final class Cards extends Column {
        final List<Rect> copies = List.of(new Rect(300, 400, 80, 40), new Rect(300, 100, 80, 40),
                new Rect(100, 110, 80, 40));

        Cards() {
            super(Map.of(), 0);
        }

        @Override
        public List<Rect> findAll(ImageTemplate picture, BufferedImage frame) {
            return picture.id().equals("go") ? copies : List.of();
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {
            done.add("click " + at.x() + "," + at.y());
        }
    }

    /**
     * A look cut wider than its button, a node's card that says Max level, says where the button is by where each was
     * cut (Firestone's Tree I).
     */
    @Test
    void aLookCutElsewhereSaysWhereItsButtonIs() {
        ImageTemplate card = new ImageTemplate("images/card.png").at(new Rect(0, 95, 300, 90));
        Button node = Button.of(TAVERN).looks(List.of(Look.of(card, State.DONE)));
        Column shown = new Column(Map.of(), 0) {
            @Override
            public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
                return picture.id().equals("card") ? Optional.of(new Rect(200, 300, 300, 90)) : Optional.empty();
            }
        };

        Reach.Seen seen = shown.seen(node, shown.look().orElseThrow()).orElseThrow();

        assertEquals(State.DONE, seen.state());
        assertEquals(new Rect(210, 305, 80, 80), seen.place(), "the button's own place, moved as the card was");
    }

    /** A usual picture cut on a maxed card matches it as well as the maxed look: the look tells the state. */
    @Test
    void aLookTiedWithTheUsualPictureTellsTheState() {
        ImageTemplate card = new ImageTemplate("images/card.png").at(new Rect(10, 100, 300, 90));
        Button node = Button.of(TAVERN).looks(List.of(Look.of(card, State.DONE)));
        Column both = new Column(Map.of(), 0) {
            @Override
            public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
                return Optional.of(picture.id().equals("card") ? new Rect(10, 100, 300, 90) : new Rect(10, 100, 80, 80));
            }
        };

        assertEquals(State.DONE, both.seen(node, both.look().orElseThrow()).orElseThrow().state());
        assertEquals(State.DONE, both.copies(node, null, Order.NORTH, both.look().orElseThrow()).getFirst().state());
    }

    private static final Button GO = Button.of(new ImageTemplate("images/go.png")).repeated();

    @Test
    void copiesAreOrderedAlongAnOrder() {
        Cards cards = new Cards();
        Rect topLeft = new Rect(100, 110, 80, 40);
        Rect topRight = new Rect(300, 100, 80, 40);
        Rect below = new Rect(300, 400, 80, 40);
        assertEquals(List.of(topLeft, topRight, below), Reach.readingOrder(cards.copies, r -> r),
                "the top row left to right, then the next row");
        Size frame = new Size(800, 600);
        assertEquals(List.of(below, topLeft, topRight), Reach.ordered(cards.copies, r -> r, Order.SOUTH, frame),
                "the bottom row first");
        assertEquals(List.of(topRight, below, topLeft), Reach.ordered(cards.copies, r -> r, Order.EAST, frame),
                "the right column first, top to bottom");
        assertEquals(List.of(topLeft, topRight, below), Reach.ordered(cards.copies, r -> r, Order.WEST, frame));
        assertEquals(below, Reach.ordered(cards.copies, r -> r, Order.CENTER, frame).getFirst(), "nearest (400, 300)");
        assertEquals(topRight, Reach.ordered(cards.copies, r -> r, Order.NORTH_EAST, frame).getFirst());
        assertEquals(below, Reach.ordered(cards.copies, r -> r, Order.SOUTH_EAST, frame).getFirst());
    }

    @Test
    void aPickClicksThatCopyAlongItsOrder() {
        Cards cards = new Cards();
        assertTrue(cards.perform(GO, null, Pick.nth(2, Order.NORTH), cards.look().orElseThrow()));
        assertEquals(List.of("click 340,120"), cards.done);
        assertFalse(cards.perform(GO, null, Pick.nth(4, Order.NORTH), cards.look().orElseThrow()), "no fourth copy");
        assertTrue(cards.perform(GO, null, Pick.first(Order.SOUTH), cards.look().orElseThrow()));
        assertEquals("click 340,420", cards.done.getLast());
        assertTrue(cards.perform(GO, null, null, cards.look().orElseThrow()), "no pick: the first");
        assertEquals("click 140,130", cards.done.getLast());
        assertEquals(3, cards.done.size());
    }

    @Test
    void eachClicksEveryCopyLettingTheGameSettleBetween() {
        Cards cards = new Cards();
        assertTrue(cards.perform(GO, null, Pick.each(Order.NORTH), cards.look().orElseThrow()));
        assertEquals(List.of("click 340,420", "click 340,120", "click 140,130"), cards.done,
                "last to first: a card claimed and gone shifts none still to press");
        assertEquals(2 * Reach.CLICK_SETTLE_MS, cards.now);
    }

    @Test
    void aPickReadsInWords() {
        assertEquals("#2 from the north", Pick.nth(2, Order.NORTH).label());
        assertEquals("each from the south-west", Pick.each(Order.SOUTH_WEST).label());
        assertEquals("until none", Pick.untilNone().label());
        assertEquals(Pick.first(), new Pick(null, 7, null), "a first pick holds no nth and reads north");
    }

    @Test
    void theBandRunsAlongTheMembersGrownByTheLargest() {
        assertEquals(new Rect(-70, 20, 240, 740), Reach.band(COLUMN));
        assertEquals(null, Reach.band(null));
        assertEquals(null, Reach.band(Strip.of(Scroll.wheel(-1), List.of(new ImageTemplate("images/x.png")))),
                "members cut nowhere: anywhere");
    }
}
