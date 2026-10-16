package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Input;
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

/** An input in a list: found anywhere in it, and scrolled to while it is not there. */
class ReachTest {

    private static final Rect COLUMN = new Rect(0, 100, 120, 600);

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    /**
     * A column of icons: each at its slot, 100 px apart down from y 100, and a scroll over {@code list} moving them up
     * one slot per notch down. Time is counted, not waited.
     */
    private static final class Column implements Reach {
        final Map<String, Integer> slots;
        final List<String> done = new ArrayList<>();
        int scrolled;
        long now;

        Column(Map<String, Integer> slots) {
            this.slots = slots;
        }

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            if (picture.id().equals("list")) return Optional.of(new Rect(10, 400, 100, 100));
            Integer slot = slots.get(picture.id());
            if (slot == null || slot - scrolled < 0) return Optional.empty();
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
            scrolled -= notches;
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
    void aButtonInItsListIsFoundAtAnotherSlot() {
        // Cut at the first slot; an icon above it came, so it shows at the fourth.
        Column column = new Column(Map.of("campaign", 3));
        Input campaign = Input.click(picture("campaign").at(new Rect(10, 100, 80, 80))).within(COLUMN);

        assertTrue(column.perform(campaign, column.look().orElseThrow()));
        assertEquals(List.of("click 440"), column.done);

        Column below = new Column(Map.of("campaign", 7));
        assertFalse(below.perform(campaign, below.look().orElseThrow()), "below the column's end it is not in it");
        assertEquals(List.of(), below.done);
    }

    @Test
    void theScrollIsDoneUntilTheButtonShows() {
        Column column = new Column(Map.of("war_machines", 8));
        Input scroll = Input.scroll(picture("list"), -1);
        Input machines = Input.click(picture("war_machines")).within(COLUMN).scrollTo(scroll);

        assertTrue(column.perform(machines, column.look().orElseThrow()));
        // Slot 8 is the sixth in the column after three notches down: 100 + 100 * 5, centred.
        assertEquals(List.of("scroll -1", "scroll -1", "scroll -1", "click 640"), column.done);
        assertEquals(3 * Reach.SCROLL_SETTLE_MS, column.now, "each scroll is let settle");
    }

    @Test
    void aButtonNoScrollBringsIsGivenUpOn() {
        Column column = new Column(Map.of());
        Input machines = Input.click(picture("war_machines")).within(COLUMN)
                .scrollTo(Input.scroll(picture("list"), -1));

        assertFalse(column.perform(machines, column.look().orElseThrow()));
        assertEquals(Input.SCROLLS, column.done.size(), "it scrolls at most SCROLLS times");
        assertTrue(column.done.stream().allMatch("scroll -1"::equals));
    }
}
