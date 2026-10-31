package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.plugin.assist.ElementNames.Named;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a frozen tree offers to pick, one row per usable node (spec § The picker, layout B): its kind and its words,
 * never a selector, and the name that finds it. A container is no row; nor a text that only labels the row it sits in
 * ("Battery" inside the Battery row), nor a node no name finds only, which is cut as a picture instead.
 */
public final class ElementRows {

    /** What the list says when the target gives no tree. */
    public static final String NO_TREE = "No tree here: this target shows no app's inputs (a game, a window). "
            + "✂ Cut a picture instead.";
    /** What the list says when the target gives a tree and it could not be read. */
    public static final String UNREAD = "The app's tree could not be read: ❄ Freeze again, or ✂ Cut a picture.";
    /** What the list says when the tree holds nothing to pick. */
    public static final String NOTHING = "Nothing to pick in this app's tree: ✂ Cut a picture instead.";
    /** What the list says when the screen moved while the tree was read. */
    public static final String MOVED = "The screen moved while reading: what is listed may be off. ❄ Freeze again "
            + "for a sure read.";

    private ElementRows() {}

    /**
     * One row: its {@code kind}, its {@code words} (its text, else its label, else the text it holds; blank for a list
     * with none), the {@code node} of the frozen tree, and the {@code named} clues that find it, words plain.
     */
    public record Row(ElementKind kind, String words, UiNode node, Named named) {

        /** How the list shows it: {@code Button "Battery"}, {@code Scrolling list}. */
        public String said() {
            return kind.displayName() + (words.isEmpty() ? "" : " \"" + words + "\"");
        }

        /** Whether a click on it presses something: what a click on the frame walks up to. */
        public boolean tappable() {
            return ElementKind.tappable(node);
        }
    }

    /** {@code tree}'s rows: its inputs, then its texts, then its lists, each in reading order. */
    public static List<Row> of(UiTree tree) {
        List<Row> rows = new ArrayList<>();
        for (UiNode node : tree.all()) {
            Optional<ElementKind> kind = ElementKind.of(node);
            if (kind.isEmpty() || labels(node, tree)) continue;
            ElementNames.name(node, tree, Words::of)
                    .ifPresent(named -> rows.add(new Row(kind.get(), words(node), node, named)));
        }
        rows.sort(Comparator.<Row>comparingInt(ElementRows::group)
                .thenComparingInt(row -> row.node().bounds().y)
                .thenComparingInt(row -> row.node().bounds().x));
        return List.copyOf(rows);
    }

    /** The list's first line: {@code Found on Settings: 9 inputs, 4 texts}; {@code screen} null for none of the map. */
    public static String found(String screen, List<Row> rows) {
        if (rows.isEmpty()) return NOTHING;
        long texts = rows.stream().filter(row -> group(row) == 1).count();
        long lists = rows.stream().filter(row -> group(row) == 2).count();
        long inputs = rows.size() - texts - lists;
        return "Found " + (screen == null ? "here" : "on " + screen) + ": " + count(inputs, "input") + ", "
                + count(texts, "text") + (lists == 0 ? "" : ", " + count(lists, "list"));
    }

    /**
     * The row a click at {@code point}, in the frame's pixels, picks: the nearest tappable row from the smallest node
     * there up, else the smallest row there; empty when no row is there.
     */
    public static Optional<Row> at(List<Row> rows, UiTree tree, Point point) {
        Map<UiNode, Row> byNode = new IdentityHashMap<>();
        rows.forEach(row -> byNode.put(row.node(), row));
        List<UiNode> hits = tree.at(point);
        if (hits.isEmpty()) return Optional.empty();
        for (UiNode up = hits.getFirst(); up != null; up = tree.parentOf(up)) {
            Row row = byNode.get(up);
            if (row != null && row.tappable()) return Optional.of(row);
        }
        return hits.stream().map(byNode::get).filter(row -> row != null).findFirst();
    }

    /** The smallest node at {@code point}: what the pointer outlines. */
    public static Optional<Rectangle> under(UiTree tree, Point point) {
        return tree.at(point).stream().findFirst().map(UiNode::bounds);
    }

    /** How {@code row} is found, in words, and what to mind: never its id or bounds. */
    public static String details(Row row) {
        StringBuilder said = new StringBuilder("Found by ").append(clues(row.named().element())).append('.');
        if (row.named().repeated()) {
            said.append("\nOne of the copies a list shows: saved as a repeated input, which a body picks among.");
        }
        if (row.named().byIndex()) said.append("\nTold apart by its place only: it breaks when the list changes.");
        if (row.named().byText()) said.append("\n").append(byTextWarning());
        return said.toString();
    }

    /** What a name relying on words warns. */
    static String byTextWarning() {
        return "Found by its words: a translated app shows others, so each language needs its own in Texts.";
    }

    static String clues(Element element) {
        List<String> clues = new ArrayList<>();
        if (element.id() != null) clues.add("its id");
        if (element.label() != null) clues.add("its label \"" + element.label().text() + "\"");
        if (element.text() != null) clues.add("its text \"" + element.text().text() + "\"");
        if (element.holding() != null) clues.add("the text \"" + element.holding().text() + "\" in it");
        if (element.inside() != null) clues.add("inside " + anchor(element.inside()));
        if (element.below() != null) clues.add("below " + anchor(element.below()));
        if (element.index() != null) clues.add("number " + (element.index() + 1) + " of those");
        return String.join(", ", clues);
    }

    private static String anchor(Element element) {
        Words words = element.text() != null ? element.text() : element.label() != null ? element.label()
                : element.holding();
        return words != null ? "\"" + words.text() + "\"" : "the view of its id";
    }

    /** 0 for an input, 1 for a text or an icon nobody presses, 2 for a list. */
    private static int group(Row row) {
        if (row.kind() == ElementKind.LIST) return 2;
        return row.tappable() ? 0 : 1;
    }

    /** Whether {@code node} is a text only labelling the tappable row it sits in. */
    private static boolean labels(UiNode node, UiTree tree) {
        if (node.text().isEmpty() || ElementKind.tappable(node)) return false;
        for (UiNode up = tree.parentOf(node); up != null; up = tree.parentOf(up)) {
            if (ElementKind.tappable(up)) return words(up).equals(node.text());
        }
        return false;
    }

    /** {@code node}'s text, else its label, else the first text inside it; blank for none. */
    static String words(UiNode node) {
        if (!node.text().isEmpty()) return node.text();
        if (!node.label().isEmpty()) return node.label();
        for (UiNode child : node.children()) {
            String held = words(child);
            if (!held.isEmpty()) return held;
        }
        return "";
    }

    private static String count(long n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }
}
