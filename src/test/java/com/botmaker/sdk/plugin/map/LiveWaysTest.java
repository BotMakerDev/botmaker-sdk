package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveWaysTest {

    private static Row row(String field, String picture) {
        return new Row(field, null, field, picture, null, "", "", "", "", List.of(), "");
    }

    @Test
    void anInputIsBoxedWhereItWasCutOnAFrameOfAnotherSize() {
        Map<String, Rect> boxes = LiveWays.boxes(List.of(row("shop", "shop_icon"), row("escape", null)),
                Map.of("shop_icon", FrameCut.of(1920, 1080, new Rect(100, 200, 40, 20))), 960, 540);
        assertEquals(Map.of("shop", new Rect(50, 100, 20, 10)), boxes);
    }

    @Test
    void aMarkerIsBoxedByItsPictureAndOneNeverCutIsLeftOut() {
        Map<String, Rect> boxes = LiveWays.markerBoxes(List.of("home_title", "home_never_cut"),
                Map.of("home_title", FrameCut.of(1920, 1080, new Rect(800, 20, 320, 60))), 960, 540);
        assertEquals(Map.of("home_title", new Rect(400, 10, 160, 30)), boxes);
    }

    @Test
    void aFrameOfAnotherShapeIsFittedWholeAndCentred() {
        // A 1000×500 cut drawn on a 1000×1000 frame: half as tall, 250 down.
        Map<String, Rect> boxes = LiveWays.boxes(List.of(row("go", "go")),
                Map.of("go", FrameCut.of(1000, 500, new Rect(0, 0, 100, 100))), 1000, 1000);
        assertEquals(new Rect(0, 250, 100, 100), boxes.get("go"));
    }

    @Test
    void aClickFallsInTheSmallestBoxThatHoldsIt() {
        Map<String, Rect> boxes = Map.of("panel", new Rect(0, 0, 500, 500), "buy", new Rect(100, 100, 50, 20));
        assertEquals(Optional.of("buy"), LiveWays.fieldAt(boxes, new Point(110, 110)));
        assertEquals(Optional.of("panel"), LiveWays.fieldAt(boxes, new Point(10, 10)));
        assertEquals(Optional.empty(), LiveWays.fieldAt(boxes, new Point(600, 10)));
    }

    @Test
    void aClickOnAnInputThenAnotherScreenProvesAWay() {
        LiveWays.Click click = new LiveWays.Click("HOME", "shop", 1_000);
        assertEquals(Optional.of(new LiveWays.Proved("HOME", "shop", "SHOP")),
                LiveWays.proved(click, "HOME", "SHOP", 3_000));
    }

    @Test
    void noWayIsProvedByAClickOffAnInputOnAnotherScreenOrLongAgo() {
        assertEquals(Optional.empty(), LiveWays.proved(new LiveWays.Click("HOME", null, 1_000), "HOME", "SHOP", 2_000));
        assertEquals(Optional.empty(), LiveWays.proved(new LiveWays.Click("BAG", "x", 1_000), "HOME", "SHOP", 2_000));
        assertEquals(Optional.empty(), LiveWays.proved(new LiveWays.Click("HOME", "shop", 1_000), "HOME", "HOME",
                2_000));
        assertEquals(Optional.empty(), LiveWays.proved(new LiveWays.Click("HOME", "shop", 1_000), "HOME", "SHOP",
                1_000 + LiveWays.RECENT_MS + 1));
        assertEquals(Optional.empty(), LiveWays.proved(null, "HOME", "SHOP", 2_000));
    }
}
