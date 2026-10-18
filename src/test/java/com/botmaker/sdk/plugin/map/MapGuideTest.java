package com.botmaker.sdk.plugin.map;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapGuideTest {

    @Test
    void theLegendRepeatsTheTooltipsItKeeps() {
        String said = MapGuide.sections().stream().flatMap(s -> s.entries().stream())
                .map(MapGuide.Entry::meaning).collect(Collectors.joining("\n"));
        for (String tip : List.of(MapGuide.OTHER_WAYS, MapGuide.TIDY, MapGuide.PNG, MapGuide.LOCKED,
                MapGuide.FOLDED, MapGuide.FILTER_OWES, MapGuide.FILTER_UNTRIED, MapGuide.TRY_NEXT,
                MapGuide.CUT_INPUTS, MapGuide.CUT_MARKER, MapGuide.GO_HERE, MapGuide.ADD_MARKER, MapGuide.LEADS_TO,
                MapGuide.REMOVE_BACK, MapGuide.LIVE, MapGuide.FREEZE, MapGuide.MISSED, MapGuide.RETAKE_FROZEN,
                MapGuide.NEW_FROM_FROZEN, MapGuide.RECORD_WAY, MapGuide.CUT_FROZEN, MapGuide.CUT_IGNORE,
                MapGuide.CUT_SAVE)) {
            assertTrue(said.contains(tip), tip);
        }
    }

    @Test
    void theLegendNamesEveryPanelButtonAndRowMenu() {
        List<String> signs = MapGuide.sections().stream().flatMap(s -> s.entries().stream())
                .map(MapGuide.Entry::sign).toList();
        for (String sign : List.of("▶ Try", "▶ Try next", "✂ Cut inputs", "✂ Cut marker", "🧭 Go here", "⋯ Screen",
                "Result", "Information only", "Comes and goes", "Never…", "Locked by…", "Use as the way back", "solid", "dashed")) {
            assertTrue(signs.contains(sign), sign);
        }
    }

    @Test
    void aPictureZoomsInWholePixels() {
        double most = PictureView.MOST_ZOOM;
        assertEquals(3, PictureView.zoomStep(2, true, 1, most));
        assertEquals(3, PictureView.zoomStep(2.5, true, 1, most));
        assertEquals(2, PictureView.zoomStep(2.5, false, 1, most));
        assertEquals(1, PictureView.zoomStep(2, false, 1, most));
        assertEquals(1, PictureView.zoomStep(1, false, 1, most));
        assertEquals(most, PictureView.zoomStep(most, true, 1, most));
    }

    @Test
    void aBigPictureStopsGrowingSooner() {
        assertEquals(2, PictureView.zoomStep(2, true, 1, 2));
        // A picture opened larger than the most stays there rather than shrinking on a zoom in.
        assertEquals(3, PictureView.zoomStep(3, true, 1, 2));
    }

    @Test
    void aShrunkPictureGrowsBackToItsOwnSize() {
        assertEquals(0.6, PictureView.zoomStep(0.3, true, 0.3, 1), 1e-9);
        assertEquals(1, PictureView.zoomStep(0.6, true, 0.3, 1));
        assertEquals(0.5, PictureView.zoomStep(1, false, 0.3, 1));
        assertEquals(0.3, PictureView.zoomStep(0.5, false, 0.3, 1));
    }
}
