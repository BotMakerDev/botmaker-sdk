package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.ScreenList;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A member of a list: found anywhere along it, and scrolled to over a sibling while it is not there. */
class ReachTest {

    /** A picture cut at {@code slot} of the column: 100 px apart down from y 100. */
    private static ImageTemplate member(String id, int slot) {
        return new ImageTemplate("images/" + id + ".png").at(new Rect(10, 100 + 100 * slot, 80, 80));
    }

    private static final ImageTemplate TAVERN = member("tavern", 0);
    private static final ImageTemplate ARENA = member("arena", 1);
    private static final ImageTemplate CAMPAIGN = member("campaign", 2);
    private static final ImageTemplate MACHINES = member("war_machines", 5);
    private static final ScreenList COLUMN = ScreenList.of(-1, List.of(TAVERN, ARENA, CAMPAIGN, MACHINES));

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
            return Optional.of(new Rect(10, 100 + 100 * (slot - scrolled), 80, 80));
        }

        @Override
        public void click(Point at) {
            done.add("click " + at.y());
        }

        @Override
        public void key(Key key) {
            done.add("key " + key.name());
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
    }

    @Test
    void aMemberIsFoundAtAnotherSlotOfItsList() {
        // Cut at the third slot; an icon above it came, so it shows at the fourth.
        Column column = new Column(Map.of("tavern", 0, "campaign", 3), 0);
        assertTrue(column.perform(Input.click(CAMPAIGN), COLUMN, column.look().orElseThrow()));
        assertEquals(List.of("click 440"), column.done);
    }

    @Test
    void aMemberOutOfViewIsScrolledToOverASiblingThatShows() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 8), 4);
        assertTrue(column.perform(Input.click(MACHINES), COLUMN, column.look().orElseThrow()));
        // Slot 8 is the sixth in view after three notches down: 100 + 100 * 5, centred.
        assertEquals(List.of("scroll -1", "scroll -1", "scroll -1", "click 640"), column.done);
        assertTrue(column.wheel.stream().allMatch(at -> at.x() == 50), "the wheel turns over a member");
        assertEquals(3 * Reach.SCROLL_SETTLE_MS, column.now, "each scroll is let settle");
    }

    /** Scrolled to its end before the run looked: no member moved, so the scroll turns back up (Home's column). */
    @Test
    void aListAtItsEndIsScrolledTheOtherWay() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "war_machines", 8), 4);
        column.scrolled = 4;
        assertTrue(column.perform(Input.click(TAVERN), COLUMN, column.look().orElseThrow()));
        assertEquals(List.of("scroll -1", "scroll 1", "scroll 1", "scroll 1", "scroll 1", "click 140"),
                column.done);
    }

    @Test
    void aMemberNeitherWayBringsIsGivenUpOn() {
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2), 4);
        assertFalse(column.perform(Input.click(MACHINES), COLUMN, column.look().orElseThrow()));
        // Down all its scrolls: past every member no end shows. Then up: four and the one that met the top.
        assertEquals(ScreenList.SCROLLS + 5, column.done.size(), column.done.toString());
        assertTrue(column.done.stream().allMatch(d -> d.startsWith("scroll")));

        // Past every member, the wheel stays where the last one showed; a list with no end below is given up on:
        // all its scrolls down, as many back up, and the one that met the top.
        Column endless = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2), 100);
        assertFalse(endless.perform(Input.click(MACHINES), COLUMN, endless.look().orElseThrow()));
        assertEquals(2 * ScreenList.SCROLLS + 1, endless.done.size(), endless.done.toString());
    }

    /** The way back searches as far past the start as the first way went: back to the start, then on past it. */
    @Test
    void theWayBackGoesPastWhereTheSearchBegan() {
        ImageTemplate far = member("far", 5);
        ScreenList list = ScreenList.of(-1, List.of(TAVERN, ARENA, CAMPAIGN, far));
        Column column = new Column(Map.of("tavern", 0, "arena", 1, "campaign", 2, "far", 8), 100);
        column.scrolled = 6;
        assertTrue(column.perform(Input.click(TAVERN), list, column.look().orElseThrow()), column.done.toString());
        // Eight down, no end met; then fourteen up, from 14 to 0, eight of them past the start.
        assertEquals(ScreenList.SCROLLS + 14 + 1, column.done.size());
        assertEquals("click 140", column.done.getLast());
    }

    @Test
    void aListOfNoNotchesScrollsNothing() {
        Column column = new Column(Map.of("tavern", 0, "war_machines", 8), 4);
        assertFalse(column.perform(Input.click(MACHINES), ScreenList.of(0, COLUMN.members()),
                column.look().orElseThrow()));
        assertEquals(List.of(), column.done);
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
        public void click(Point at) {
            done.add("click " + at.x() + "," + at.y());
        }
    }

    private static final ImageTemplate GO = new ImageTemplate("images/go.png");

    @Test
    void copiesAreCountedInReadingOrder() {
        Cards cards = new Cards();
        assertEquals(List.of(new Rect(100, 110, 80, 40), new Rect(300, 100, 80, 40), new Rect(300, 400, 80, 40)),
                Reach.readingOrder(cards.copies, r -> r), "the top row left to right, then the next row");
    }

    @Test
    void anNthInputClicksThatCopy() {
        Cards cards = new Cards();
        assertTrue(cards.perform(Input.click(GO).nth(2), cards.look().orElseThrow()));
        assertEquals(List.of("click 340,120"), cards.done);
        assertFalse(cards.perform(Input.click(GO).nth(4), cards.look().orElseThrow()), "no fourth copy");
        assertEquals(1, cards.done.size());
    }

    @Test
    void anEachInputClicksEveryCopyLettingTheGameSettleBetween() {
        Cards cards = new Cards();
        assertTrue(cards.perform(Input.click(GO).each(), cards.look().orElseThrow()));
        assertEquals(List.of("click 340,420", "click 340,120", "click 140,130"), cards.done,
                "last to first: a card claimed and gone shifts none still to press");
        assertEquals(2 * Reach.CLICK_SETTLE_MS, cards.now);
    }

    @Test
    void theBandRunsAlongTheMembersGrownByTheLargest() {
        assertEquals(new Rect(-70, 20, 240, 740), Reach.band(COLUMN));
        assertEquals(null, Reach.band(null));
        assertEquals(null, Reach.band(ScreenList.of(-1, List.of(new ImageTemplate("images/x.png")))),
                "members cut nowhere: anywhere");
    }
}
