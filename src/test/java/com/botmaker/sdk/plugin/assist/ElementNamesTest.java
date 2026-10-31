package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.sdk.plugin.assist.ElementNames.Named;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ElementNames}' ladder on synthetic trees: a two-pane settings screen and a downloads list. */
class ElementNamesTest {

    private static final UiTree SETTINGS = tree("settings-two-pane.xml", "com.example.settings");
    private static final UiTree DOWNLOADS = tree("downloads-list.xml", "com.example.files");

    private static UiTree tree(String name, String app) {
        try (InputStream in = ElementNamesTest.class.getResourceAsStream("/tree/" + name)) {
            return UiTree.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).app(app);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static UiNode node(UiTree tree, Predicate<UiNode> which, int nth) {
        return tree.all().stream().filter(which).toList().get(nth);
    }

    private static Named name(UiNode picked, UiTree tree) {
        Named named = ElementNames.name(picked, tree, Words::of).orElseThrow();
        assertFinds(picked, named, tree);
        return named;
    }

    /** Whatever the rung, the name finds the picked input: once, or among its copies. */
    private static void assertFinds(UiNode picked, Named named, UiTree tree) {
        List<UiNode> found = ElementMatch.matches(named.element(), tree);
        assertTrue(found.stream().anyMatch(n -> n == picked), "the name finds what was picked: " + named);
        if (!named.repeated()) assertEquals(1, found.size(), "the name finds only what was picked: " + named);
    }

    @Test
    void everyNameFindsWhatWasPicked() {
        for (UiTree tree : List.of(SETTINGS, DOWNLOADS)) {
            for (UiNode picked : tree.all()) {
                ElementNames.name(picked, tree, Words::of).ifPresent(n -> assertFinds(picked, n, tree));
            }
        }
    }

    @Test
    void uniqueIdNamedById() {
        Named named = name(node(SETTINGS, n -> n.id().endsWith("/wifi_switch"), 0), SETTINGS);
        assertEquals(Element.byId("wifi_switch"), named.element());
        assertFalse(named.byText());
        assertFalse(named.byIndex());
    }

    @Test
    void listRowNamedByIdAndText() {
        Named named = name(node(SETTINGS, n -> n.text().equals("Battery"), 0), SETTINGS);
        assertEquals(Element.byId("title").text(Words.of("Battery")), named.element());
        assertTrue(named.byText());
    }

    @Test
    void duplicateTextNamedInsideContainer() {
        Named named = name(node(SETTINGS, n -> n.text().equals("Network & internet"), 0), SETTINGS);
        assertEquals(Element.byId("title").text(Words.of("Network & internet")).inside(Element.byId("list_pane")),
                named.element());
    }

    @Test
    void tappableRowNamedByTheTextItHolds() {
        UiNode row = node(SETTINGS, n -> n.text().isEmpty() && n.id().isEmpty() && n.bounds().y == 100, 0);
        Named named = name(row, SETTINGS);
        assertEquals(Element.byHolding(Words.of("Network & internet")).inside(Element.byId("list_pane")),
                named.element());
    }

    @Test
    void neighbourNamesWhatNoContainerSeparates() {
        Named named = name(node(DOWNLOADS, n -> n.id().endsWith("/share"), 0), DOWNLOADS);
        assertEquals(Element.byId("share").below(Element.byId("downloads")), named.element());
        assertFalse(named.byIndex());
    }

    @Test
    void twinRowsAreRepeatedNotIndexed() {
        Named named = name(node(DOWNLOADS, n -> n.id().endsWith("/remove"), 1), DOWNLOADS);
        assertTrue(named.repeated());
        assertFalse(named.byIndex());
        assertNull(named.element().index());
        assertEquals(Element.byId("remove").text(Words.of("Remove")).inside(Element.byId("downloads")),
                named.element());
    }

    @Test
    void indexOnlyAsLastRungFlagged() {
        Named named = name(node(DOWNLOADS, n -> n.id().endsWith("/share"), 1), DOWNLOADS);
        assertTrue(named.byIndex());
        assertFalse(named.repeated());
        assertEquals(Element.byId("share").index(1), named.element());
    }

    @Test
    void aTappableRowIsNamedByTheTitleInItsWrapper() {
        UiNode row = node(DOWNLOADS, n -> n.has(Trait.CLICKABLE) && n.bounds().y == 1700, 0);
        Named named = name(row, DOWNLOADS);
        assertEquals(Element.byHolding(Words.of("Storage settings")), named.element());
    }

    @Test
    void textsAreAskedOnlyForTheNamesWords() {
        List<String> asked = new ArrayList<>();
        UiNode network = node(SETTINGS, n -> n.text().equals("Network & internet"), 0);
        ElementNames.name(network, SETTINGS, text -> {
            asked.add(text);
            return Words.of(text);
        }).orElseThrow();
        assertEquals(List.of("Network & internet"), asked);
    }

    @Test
    void aNodeWithNothingToBeFoundByHasNoName() {
        UiNode toolbar = node(DOWNLOADS, n -> n.id().isEmpty() && n.bounds().y == 1000, 0);
        assertTrue(ElementNames.name(toolbar, DOWNLOADS, Words::of).isEmpty());
    }

    @Test
    void nameNeverHoldsBounds() {
        Set<String> classes = SETTINGS.all().stream().map(UiNode::className).collect(Collectors.toSet());
        for (UiNode picked : SETTINGS.all()) {
            ElementNames.name(picked, SETTINGS, Words::of).ifPresent(n -> assertHoldsNo(n.element(), classes));
        }
    }

    private static void assertHoldsNo(Element element, Set<String> classes) {
        if (element == null) return;
        for (RecordComponent part : Element.class.getRecordComponents()) {
            assertNotEquals(Rectangle.class, part.getType(), part.getName());
            try {
                Object value = part.getAccessor().invoke(element);
                if (value instanceof String s) assertFalse(classes.contains(s), part.getName() + " is a class name");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        assertHoldsNo(element.inside(), classes);
        assertHoldsNo(element.below(), classes);
    }
}
