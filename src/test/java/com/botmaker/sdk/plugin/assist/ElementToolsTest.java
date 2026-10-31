package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.plugin.assist.ElementSaves.As;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.botmaker.sdk.plugin.assist.ElementRowsTest.DOWNLOADS;
import static com.botmaker.sdk.plugin.assist.ElementRowsTest.SETTINGS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** list_elements and save_element: rows said as the picker says them, one chosen by its words; Discovery tries them. */
class ElementToolsTest {

    @Test
    void listElementsHidesIdsAndBounds() {
        String listed = ElementTools.listed(ElementRows.of(SETTINGS));
        assertTrue(listed.contains("- Button \"Battery\": by its id, its text \"Battery\"; as button or marker"),
                listed);
        for (String never : List.of("title", "108", "306", "TextView", "com.example", "android:")) {
            assertFalse(listed.contains(never), listed + " holds " + never);
        }
    }

    @Test
    void copiesAreListedOnce() {
        String listed = ElementTools.listed(ElementRows.of(DOWNLOADS));
        assertEquals(1, listed.lines().filter(l -> l.startsWith("- Button \"Remove\"")).count(), listed);
        assertTrue(listed.contains("saved repeated"), listed);
    }

    @Test
    void saveElementByWords() {
        ElementTools.Chosen chosen = ElementTools.chosen(ElementRows.of(SETTINGS), " battery ", null, null);
        assertNull(chosen.refusal());
        assertEquals("Button \"Battery\"", chosen.pick().row().said());
        assertEquals(As.BUTTON, chosen.pick().as(), "a tappable row is a button first");
        assertEquals(As.MARKER, ElementTools.chosen(ElementRows.of(SETTINGS), "Battery", null, "marker").pick().as());
        assertTrue(ElementTools.chosen(ElementRows.of(SETTINGS), "Battery", null, "readout").refusal()
                .contains("saved as button or marker, not readout"));
        // Copies of a list's row are one input: the first stands for them, saved repeated.
        ElementTools.Chosen copy = ElementTools.chosen(ElementRows.of(DOWNLOADS), "Remove", null, null);
        assertNull(copy.refusal());
        assertTrue(copy.pick().row().named().repeated());
        assertEquals(ElementKind.LIST, ElementTools.chosen(ElementRows.of(DOWNLOADS), "Remove", "Scrolling list", null)
                .pick().row().kind(), "kind names the list that says its first row's words");
    }

    @Test
    void saveElementAmbiguousListsCandidates() {
        ElementTools.Chosen none = ElementTools.chosen(ElementRows.of(SETTINGS), "Batt", null, null);
        assertTrue(none.refusal().contains("Close: Button \"Battery\""), none.refusal());
        ElementTools.Chosen wrongKind = ElementTools.chosen(ElementRows.of(SETTINGS), "Battery", "Toggle", null);
        assertTrue(wrongKind.refusal().startsWith("No row \"Battery\" of kind Toggle"), wrongKind.refusal());
        assertTrue(wrongKind.refusal().contains("Close: Button \"Battery\""), wrongKind.refusal());
    }

    /** An element the tree finds twice ends the call wherever it was met, said as the bot's, never as Studio busy. */
    @Test
    void anElementFoundTwiceStopsTheCallWithItsReason() {
        assertFalse(IllegalStateException.class.isAssignableFrom(ToolReach.Stopped.class),
                "the catches for Studio busy must never take it");
        AgentReply reply = ToolTiming.time("try_input", null, () -> {
            throw new ToolReach.Stopped("Button \"Battery\" is found twice in the tree");
        });
        assertTrue(reply.isRefused());
        assertTrue(reply.parts().getFirst().text().startsWith("Stopped: Button \"Battery\" is found twice"),
                reply.parts().getFirst().text());
        assertTrue(reply.parts().getFirst().text().contains("may have acted"), "it never says nothing was done");
    }

    /** On an app, Discovery saves the tree's inputs and try_input presses them by field, never as pictures. */
    @Test
    void discoveryTriesTreeInputs() {
        Element battery = Element.byId("title").text(Words.of("Battery")).shot("elements/battery.png");
        MapScreen settings = MapScreen.of(Setup.named("Settings")).withInput("battery", Button.of(battery))
                .withInput("gear", Button.of(new ImageTemplate("gear")));
        assertEquals(Optional.of("battery"), MapTools.elementField(settings, "Battery"));
        assertEquals(Optional.empty(), MapTools.elementField(settings, "gear"), "a picture's button is matched");
        assertEquals("Battery", MapTools.nameOf(Button.of(battery), "battery"), "it opens a screen of its words");
        assertEquals("Wifi toggle", MapTools.nameOf(Button.of(Element.byId("wifi")), "wifi_toggle"));
        assertTrue(Protocol.Stage.DISCOVERY.section().contains("list_elements instead: save_element"));
        assertTrue(LookTools.TREE.contains("list_elements"));
    }
}
