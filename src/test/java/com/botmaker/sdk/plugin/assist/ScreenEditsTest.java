// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.Markers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The names {@link ScreenEdits} gives what it adds to the bot, and the markers it adds. */
class ScreenEditsTest {

    /** A field's text kept in {@code Texts} is named after it, as a constant, never a name taken already. */
    @Test
    void aTextsConstantIsNamedAfterItsText() {
        assertEquals("DRAGONS_GUILD", ScreenEdits.textName("  Dragons guild! ", Set.of()));
        assertEquals("DRAGONS_2", ScreenEdits.textName("dragons", Set.of("DRAGONS")));
        assertEquals("DRAGONS_3", ScreenEdits.textName("dragons", Set.of("DRAGONS", "DRAGONS_2")));
        assertEquals("TEXT_42", ScreenEdits.textName("42", Set.of()));
        assertEquals("TEXT", ScreenEdits.textName("…", Set.of()));
        assertEquals("A_VERY_LONG_SEARCH_THAT_GOES_ON", ScreenEdits.textName(
                "a very long search that goes on and on", Set.of()), "cut at a word's end");
    }

    /**
     * add_marker by an element (2026-11-27): added beside the screen's pictures, or as one it never holds; held already
     * is said, and one on the other side is refused. The element is named as typed, with or without its class.
     */
    @Test
    void anElementMarksAScreenBesideItsPictures() {
        Element wifi = Element.byId("wifi_title");
        Markers was = new Markers(List.of(new ImageTemplate("images/wifi.png")), List.of());

        ScreenEdits.Marked added = ScreenEdits.withElement(was, "WIFI_TITLE", wifi, false);
        assertEquals(List.of(wifi), added.now().elements());
        assertEquals(1, added.now().shown().size(), "its picture kept");

        ScreenEdits.Marked absent = ScreenEdits.withElement(was, "WIFI_TITLE", wifi, true);
        assertEquals(List.of(wifi), absent.now().absentElements());

        ScreenEdits.Marked again = ScreenEdits.withElement(added.now(), "WIFI_TITLE", wifi, false);
        assertNull(again.now());
        assertTrue(again.said().startsWith("It is told by"), again.said());

        ScreenEdits.Marked other = ScreenEdits.withElement(added.now(), "WIFI_TITLE", wifi, true);
        assertTrue(other.said().startsWith(ScreenEdits.NOT_WRITTEN), other.said());

        assertEquals("WIFI_TITLE", MapEdits.elementName(" Elements.WIFI_TITLE "));
        assertEquals("WIFI_TITLE", MapEdits.elementName("WIFI_TITLE"));
    }
}
