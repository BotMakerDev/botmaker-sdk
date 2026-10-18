package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.plugin.map.CutLayer.Drawn;
import com.botmaker.sdk.plugin.map.CutLayer.Handle;
import com.botmaker.sdk.plugin.screen.CutShape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cut drawn on the board, in the frame's pixels. */
class CutLayerTest {

    @Test
    void aBoxDrawnAtHalfSizeIsTwiceAsBigOnTheFrame() {
        assertEquals(new Rect(20, 40, 60, 30), CutLayer.toFrame(10, 20, 30, 15, 0.5, 1280, 720));
    }

    @Test
    void aBoxStaysOnTheFrame() {
        assertEquals(new Rect(90, 0, 10, 50), CutLayer.toFrame(90, -5, 40, 55, 1, 100, 100));
        // Never empty, at the frame's last pixel either.
        assertEquals(new Rect(99, 99, 1, 1), CutLayer.toFrame(120, 120, 5, 5, 1, 100, 100));
    }

    @Test
    void theSkippedPartIsWhatLiesInTheCut() {
        Rect cut = new Rect(10, 10, 20, 20);
        assertEquals(Optional.of(new Rect(25, 10, 5, 5)), CutLayer.inside(new Rect(25, 5, 10, 10), cut));
        assertEquals(Optional.empty(), CutLayer.inside(new Rect(40, 40, 5, 5), cut));
        assertTrue(CutLayer.holds(cut, new Rect(12, 12, 4, 4)));
        assertFalse(CutLayer.holds(cut, new Rect(25, 5, 30, 30)));
    }

    @Test
    void aFreeShapeClosesOnAClickNearItsFirstCornerOnceItHasThree() {
        List<Point> two = List.of(new Point(10, 10), new Point(50, 10));
        assertFalse(CutLayer.closes(two, new Point(11, 11), 4), "two corners are no shape yet");
        List<Point> three = List.of(new Point(10, 10), new Point(50, 10), new Point(30, 40));
        assertTrue(CutLayer.closes(three, new Point(12, 12), 4));
        assertFalse(CutLayer.closes(three, new Point(20, 20), 4));
    }

    @Test
    void aCutIsPickedOnTopFirstAndByItsNumber() {
        List<Drawn> cuts = List.of(box(0, 0, 100, 100), box(20, 20, 30, 30));
        assertEquals(1, CutLayer.hit(cuts, new Point(25, 25)), "the one drawn last is on top");
        assertEquals(0, CutLayer.hit(cuts, new Point(80, 80)));
        assertEquals(-1, CutLayer.hit(cuts, new Point(150, 10)));
        // Drawn at half size: cut 2's number sits at (10, 10) on the board.
        assertEquals(1, CutLayer.badgeHit(cuts, 14, 14, 0.5));
        assertEquals(-1, CutLayer.badgeHit(cuts, 40, 40, 0.5));
    }

    @Test
    void aMovedCutTakesItsCornersAndSkippedPartAndStaysOnTheFrame() {
        Drawn free = new Drawn(new Rect(10, 10, 20, 20), CutShape.POLYGON,
                List.of(new Point(10, 10), new Point(30, 10), new Point(20, 30)), new Rect(15, 15, 4, 4));
        Drawn moved = CutLayer.moved(free, 5, 7, 100, 100);
        assertEquals(new Rect(15, 17, 20, 20), moved.box());
        assertEquals(new Point(15, 17), moved.points().getFirst());
        assertEquals(new Rect(20, 22, 4, 4), moved.ignore());
        assertEquals(new Rect(80, 0, 20, 20), CutLayer.moved(free, 500, -500, 100, 100).box(), "kept on the frame");
    }

    @Test
    void aCornerDraggedResizesFromTheOppositeOne() {
        Drawn cut = new Drawn(new Rect(10, 10, 20, 20), CutShape.RECT, List.of(), new Rect(12, 12, 16, 16));
        Drawn grown = CutLayer.resized(cut, Handle.BOTTOM_RIGHT, new Point(50, 40), 100, 100);
        assertEquals(new Rect(10, 10, 40, 30), grown.box());
        Drawn flipped = CutLayer.resized(cut, Handle.TOP_LEFT, new Point(40, 35), 100, 100);
        assertEquals(new Rect(30, 30, 10, 5), flipped.box(), "dragged past the kept corner, it turns over");
        assertNull(flipped.ignore(), "its skipped part no longer lies in it");
        Drawn shrunk = CutLayer.resized(cut, Handle.BOTTOM_RIGHT, new Point(14, 14), 100, 100);
        assertEquals(new Rect(12, 12, 2, 2), shrunk.ignore(), "the skipped part keeps what lies in it");
        Drawn free = new Drawn(new Rect(0, 0, 10, 10), CutShape.POLYGON, List.of(new Point(0, 0)), null);
        assertEquals(free, CutLayer.resized(free, Handle.TOP_LEFT, new Point(5, 5), 100, 100));
    }

    private static Drawn box(int x, int y, int w, int h) {
        return new Drawn(new Rect(x, y, w, h), CutShape.RECT, List.of(), null);
    }
}
