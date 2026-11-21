// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenCanvasSpreadTest {

    /** A first drag pins every card where it is, except cards a collapsed layout put on one corner. */
    @Test
    void cardsSharingACornerAreNotPinned() {
        Placement laid = new Placement(Map.of("A", new Pt(0, 0), "B", new Pt(621, 781), "C", new Pt(621, 781),
                "D", new Pt(300, 0), "E", new Pt(9, 9)), Set.of("E"), 0);

        assertEquals(Map.of("A", new Pt(0, 0), "D", new Pt(300, 0)), ScreenCanvas.spread(laid));
    }
}
