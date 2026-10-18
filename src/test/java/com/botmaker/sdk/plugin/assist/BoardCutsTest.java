package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.plugin.assist.BoardCuts.Cut;
import com.botmaker.sdk.plugin.assist.MapTools.Mark;
import com.botmaker.sdk.plugin.assist.PictureTools.Box;
import com.botmaker.sdk.plugin.screen.CutShape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cut drawn by hand is the box crop_pictures would have been given for it. */
class BoardCutsTest {

    private static final Rect BOX = new Rect(10, 20, 30, 40);

    private static Cut cut(BoardCuts.As as) {
        return new Cut("collect", BOX, CutShape.RECT, List.of(), null, as);
    }

    @Test
    void aMarkerIsTheSetAPopupItsFlag() {
        Box box = BoardCuts.box(cut(new BoardCuts.Marker(true)));
        assertEquals(Mark.SET, box.marker());
        assertEquals(Boolean.TRUE, box.popup());
        assertEquals(10, box.x());
        assertEquals(40, box.height());
        assertEquals(Mark.ADD, BoardCuts.box(cut(new BoardCuts.MoreMarker(false))).marker());
        assertEquals(Mark.ABSENT, BoardCuts.box(cut(new BoardCuts.Absent())).marker());
    }

    @Test
    void aButtonTakesWhenItShowsAndALookWhatItIsOf() {
        Box press = BoardCuts.box(cut(new BoardCuts.Press(true, " Bot::eventOn ")));
        assertNull(press.marker());
        assertEquals(Boolean.TRUE, press.sometimes());
        assertEquals("Bot::eventOn", press.when());
        Box look = BoardCuts.box(cut(new BoardCuts.LookOf("collect_button", State.DISABLED)));
        assertEquals("collect_button", look.variantOf());
        assertEquals(State.DISABLED, look.state());
        assertEquals(Boolean.TRUE, BoardCuts.box(cut(new BoardCuts.Back())).back());
        Box screenLook = BoardCuts.box(cut(new BoardCuts.ScreenLook("Halloween", true, "")));
        assertEquals(Mark.SET, screenLook.marker());
        assertEquals("Halloween", screenLook.screenLook());
        assertNull(screenLook.when());
    }

    @Test
    void aPolygonGoesByItsCornersAndTheSkippedPartAlong() {
        List<Point> corners = List.of(new Point(10, 20), new Point(40, 20), new Point(25, 60));
        Box box = BoardCuts.box(new Cut("gem", BOX, CutShape.POLYGON, corners, new Rect(12, 22, 4, 4),
                new BoardCuts.Press(false, null)));
        assertNull(box.x());
        assertNull(box.width());
        assertEquals(3, box.points().size());
        assertEquals(40, box.points().get(1).x());
        assertEquals(12, box.ignore().x());
        assertNull(box.sometimes());
    }

    @Test
    void onlyAScreensPicturesGoToCropPictures() {
        assertTrue(BoardCuts.picture(cut(new BoardCuts.Member(null, false, -3))));
        assertTrue(BoardCuts.picture(cut(new BoardCuts.Back())));
        assertFalse(BoardCuts.picture(cut(new BoardCuts.Place(null))));
        assertFalse(BoardCuts.picture(cut(new BoardCuts.Digits("12"))));
        assertFalse(BoardCuts.picture(cut(new BoardCuts.Recut("collect"))));
    }

    @Test
    void aRecutIsReplacesBox() {
        PictureTools.Replace replace = BoardCuts.replace(cut(new BoardCuts.Recut("collect")), "collect");
        assertEquals("collect", replace.picture());
        assertEquals(30, replace.width());
        assertEquals(CutShape.RECT, replace.shape());
    }
}
