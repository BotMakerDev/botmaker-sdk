package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ElementMatch} on a synthetic two-pane settings screen: 14 rows share {@code android:id/title}. */
class ElementMatchTest {

    static final String SETTINGS = "com.example.settings";

    /** The synthetic dump {@code name}, every window of it. */
    static UiTree whole(String name) {
        try (InputStream in = ElementMatchTest.class.getResourceAsStream("/tree/" + name)) {
            return UiTree.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static UiTree tree(String name, String app) {
        return whole(name).app(app);
    }

    private static final UiTree TREE = tree("settings-two-pane.xml", SETTINGS);

    private static List<UiNode> find(Element element) {
        return ElementMatch.matches(element, TREE);
    }

    private static Words words(String text) {
        return Words.of(text);
    }

    @Test
    void idAloneMatchesOne() {
        List<UiNode> found = find(Element.byId("wifi_switch"));
        assertEquals(1, found.size());
        assertEquals("com.example.settings:id/wifi_switch", found.getFirst().id());
    }

    @Test
    void sharedIdMatchesFourteen() {
        assertEquals(14, find(Element.byId("title")).size());
    }

    @Test
    void idPlusTextMatchesOne() {
        List<UiNode> found = find(Element.byId("title").text(words("Battery")));
        assertEquals(1, found.size());
        assertEquals("Battery", found.getFirst().text());
    }

    @Test
    void insideSeparatesPanes() {
        Element network = Element.byText(words("Network & internet"));
        assertEquals(2, find(network).size());
        List<UiNode> left = find(network.inside(Element.byId("list_pane")));
        assertEquals(1, left.size());
        assertEquals(108, left.getFirst().bounds().x);
    }

    @Test
    void belowUsesNeighbour() {
        List<UiNode> found = find(Element.byId("title").below(Element.byId("title").text(words("Battery"))));
        assertEquals(List.of("Storage"), found.stream().map(UiNode::text).toList());
    }

    @Test
    void labelMatchesContentDesc() {
        List<UiNode> found = find(Element.byLabel(words("Wi‑Fi")));
        assertEquals(1, found.size());
        assertTrue(found.getFirst().has(Trait.CHECKABLE));
    }

    @Test
    void holdingMatchesDescendantText() {
        List<UiNode> found = find(Element.byHolding(words("Network & internet")));
        // The row holding the title and the page around the other one; never the list or the window around them.
        assertEquals(2, found.size());
        assertTrue(found.getFirst().has(Trait.CLICKABLE));
        assertEquals("com.example.settings:id/detail_pane", found.get(1).id());
        List<UiNode> row = find(Element.byHolding(words("Network & internet")).inside(Element.byId("list_pane")));
        assertEquals(List.of(found.getFirst()), row);
    }

    @Test
    void textMatchesWordsAsCaptionsRead() {
        assertEquals(1, find(Element.byText(words("battery "))).size());
        assertEquals(1, find(Element.byText(words("BATTERY"))).size());
        assertEquals(0, find(Element.byText(words("Batteries left"))).size());
        // The tree states its text: no letter is forgiven, as OCR's misreads are.
        assertEquals(0, find(Element.byText(words("Sond"))).size());
        assertEquals(0, find(Element.byText(words("Displays"))).size());
    }

    @Test
    void holdingFindsTheTappableRowNotItsWrapper() {
        UiTree files = tree("downloads-list.xml", "com.example.files");
        List<UiNode> found = ElementMatch.matches(Element.byHolding(words("Storage settings")), files);
        assertEquals(1, found.size());
        assertTrue(found.getFirst().has(Trait.CLICKABLE));
    }

    @Test
    void indexPicksInTreeOrder() {
        assertEquals(List.of("Battery"), find(Element.byId("title").index(4)).stream().map(UiNode::text).toList());
        assertEquals(List.of(), find(Element.byId("title").index(14)));
    }

    @Test
    void anIdWithItsPackageIsTheWholeId() {
        assertEquals(1, find(Element.byId("com.example.settings:id/wifi_switch")).size());
        assertEquals(0, find(Element.byId("com.other:id/wifi_switch")).size());
    }

    @Test
    void anIdOfAnotherAppsPackageNeedsItsPackage() {
        // The status bar's clock is systemui's: its own package, so its short id finds it in the whole tree.
        assertEquals(1, ElementMatch.matches(Element.byId("clock"), whole("settings-two-pane.xml")).size());
        assertEquals(0, ElementMatch.matches(Element.byId("clock"), TREE).size());
    }

    @Test
    void noClueRefused() {
        assertThrows(IllegalArgumentException.class, () -> Element.byId(""));
        assertThrows(IllegalArgumentException.class, () -> Element.byText(null));
        assertThrows(IllegalArgumentException.class, () -> Element.byText(words("x")).id(" "));
        assertThrows(IllegalArgumentException.class, () -> Element.byId("title").inside(null));
        assertThrows(IllegalArgumentException.class, () -> Element.byId("title").index(-1));
    }
}
