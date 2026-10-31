package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.plugin.assist.ElementRows.Row;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the picker's list offers off a frozen tree, and what a click on the frame picks, on synthetic screens. */
class ElementRowsTest {

    static final UiTree SETTINGS = tree("settings-two-pane.xml", "com.example.settings");
    static final UiTree DOWNLOADS = tree("downloads-list.xml", "com.example.files");

    static UiTree tree(String name, String app) {
        try (InputStream in = ElementRowsTest.class.getResourceAsStream("/tree/" + name)) {
            return UiTree.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).app(app);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Row row(List<Row> rows, String said) {
        return rows.stream().filter(r -> r.said().equals(said)).findFirst().orElseThrow(() -> new AssertionError(
                said + " not in " + rows.stream().map(Row::said).toList()));
    }

    /** Inputs, then texts, then lists, counted on the first line. */
    @Test
    void listsInputsAndTextsWithCounts() {
        List<Row> rows = ElementRows.of(SETTINGS);
        assertEquals("Found on Settings: 15 inputs, 1 text, 2 lists", ElementRows.found("Settings", rows));
        assertEquals("Button \"Network & internet\"", rows.getFirst().said(), "the first input, top left");
        assertEquals(ElementKind.LIST, rows.getLast().kind(), "lists last");
        assertEquals("Found here: 15 inputs, 1 text, 2 lists", ElementRows.found(null, rows));
    }

    /** A layout with nothing of its own is no row, nor a text that only labels the row it is in. */
    @Test
    void containersNotListed() {
        List<Row> rows = ElementRows.of(SETTINGS);
        assertTrue(rows.stream().noneMatch(r -> r.node().className().endsWith("FrameLayout")));
        assertEquals(1, rows.stream().filter(r -> r.words().equals("Network & internet") && r.tappable()).count());
        assertEquals(1, rows.stream().filter(r -> r.kind() == ElementKind.TEXT).count(),
                "the detail pane's title is a text; the master row's own label is not");
        assertEquals(ElementKind.TOGGLE, row(rows, "Toggle \"Wi‑Fi\"").kind());
        assertEquals(ElementKind.FIELD, row(rows, "Text field").kind());
    }

    /** A click on a row's label picks the row: its nearest tappable node from there up. */
    @Test
    void clickOnFrameSelectsRowOfTappableParent() {
        List<Row> rows = ElementRows.of(SETTINGS);
        Row picked = ElementRows.at(rows, SETTINGS, new Point(150, 150)).orElseThrow();
        assertSame(row(rows, "Button \"Network & internet\""), picked);
        assertEquals(new Rectangle(0, 100, 700, 70), picked.node().bounds());
        // A text no tappable node holds is picked itself.
        assertEquals(ElementKind.TEXT, ElementRows.at(rows, SETTINGS, new Point(800, 80)).orElseThrow().kind());
        assertTrue(ElementRows.at(rows, SETTINGS, new Point(5000, 5000)).isEmpty(), "off every node");
    }

    /** The pointer outlines the smallest node under it, the label inside a row rather than the row. */
    @Test
    void hoverOutlinesTheSmallestNode() {
        assertEquals(new Rectangle(108, 138, 192, 32), ElementRows.under(SETTINGS, new Point(150, 150)).orElseThrow());
    }

    /** A row says its kind and words; no row, nor how it is found, shows a resource id. */
    @Test
    void rowShowsKindAndWordsNeverId() {
        List<Row> rows = ElementRows.of(SETTINGS);
        for (Row row : rows) {
            for (UiNode node : SETTINGS.all()) {
                if (node.id().isEmpty()) continue;
                String id = node.id().substring(node.id().indexOf('/') + 1);
                assertFalse(row.said().contains(id), row.said() + " shows " + id);
                assertFalse(ElementRows.details(row).contains(id), ElementRows.details(row) + " shows " + id);
            }
        }
        assertTrue(ElementRows.details(row(rows, "Button \"Battery\"")).contains("its text \"Battery\""));
    }

    /** Copies of a list's row are rows each, said repeated in their details. */
    @Test
    void copiesAreRowsSaidRepeated() {
        List<Row> rows = ElementRows.of(DOWNLOADS);
        List<Row> removes = rows.stream().filter(r -> r.said().equals("Button \"Remove\"")).toList();
        assertEquals(3, removes.size());
        assertTrue(ElementRows.details(removes.get(1)).contains("repeated input"));
    }

    /** With no tree, or nothing in it, the list sends to ✂ Cut. */
    @Test
    void noTreeSaysCutInstead() {
        assertTrue(ElementRows.NO_TREE.contains("✂ Cut"));
        assertEquals(ElementRows.NOTHING, ElementRows.found("Settings", List.of()));
        assertTrue(ElementRows.NOTHING.contains("✂ Cut"));
    }
}
