package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.ocr.LineMatch;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which nodes of a tree an {@link Element} finds: those of which every clue it names holds (after Maestro's
 * {@code Filters}). Words match letter for letter, whatever their case and the spaces or marks at their ends
 * ({@link LineMatch#sameText}). The tree is the app's own windows ({@link UiTree#app}); nodes come back in the tree's
 * order.
 *
 * <ul>
 *   <li>{@code id}: the node's id without its package, when the package is the node's app's or {@code android};
 *       an id naming its package ({@code com.lib:id/chip}) is the node's whole id.</li>
 *   <li>{@code holding}: of nested nodes holding the words, the row a person taps: a holder inside a tappable
 *       holder is the row's wrapper, and of the rest only the innermost, never the list and the window around
 *       them.</li>
 *   <li>{@code inside}: the node sits, at any depth, in a node its container finds.</li>
 *   <li>{@code below}: for each node its neighbour finds, the nearest node under it that overlaps it across; of
 *       two as near, the smaller.</li>
 *   <li>{@code index}: the one at that place among what the rest found; none past the end.</li>
 * </ul>
 */
public final class ElementMatch {

    private ElementMatch() {}

    /** The nodes of {@code tree} that {@code element} finds, in the tree's order. */
    public static List<UiNode> matches(Element element, UiTree tree) {
        return in(tree).matches(element);
    }

    /** A matcher over {@code tree}, for several elements: the tree is walked once. */
    public static Matcher in(UiTree tree) {
        return new Matcher(tree);
    }

    /**
     * One tree's nodes and parents, and what each element found in it, by identity: an anchor several elements
     * name is matched once. Not thread-safe; one per tree read.
     */
    public static final class Matcher {
        private final List<UiNode> nodes;
        private final Map<UiNode, UiNode> parents = new IdentityHashMap<>();
        private final Map<Element, List<UiNode>> found = new IdentityHashMap<>();

        private Matcher(UiTree tree) {
            nodes = tree.all();
            for (UiNode node : nodes) {
                for (UiNode child : node.children()) parents.put(child, node);
            }
        }

        /** The tree's nodes, depth first. */
        public List<UiNode> nodes() {
            return nodes;
        }

        /** The node {@code node} sits in; null for a root. */
        public UiNode parentOf(UiNode node) {
            return parents.get(node);
        }

        /** The nodes {@code element} finds, in the tree's order. */
        public List<UiNode> matches(Element element) {
            List<UiNode> known = found.get(element);
            if (known != null) return known;
            List<UiNode> hits = new ArrayList<>();
            for (UiNode node : nodes) {
                if (owns(element, node)) hits.add(node);
            }
            if (element.holding() != null) hits = tapped(hits);
            if (element.inside() != null) hits = inside(hits, matches(element.inside()));
            if (element.below() != null) hits = below(hits, matches(element.below()));
            if (element.index() != null) {
                hits = element.index() < hits.size() ? List.of(hits.get(element.index())) : List.of();
            }
            List<UiNode> result = List.copyOf(hits);
            found.put(element, result);
            return result;
        }

        /** Whether the clues about the node itself hold: id, label, text and holding. */
        private static boolean owns(Element element, UiNode node) {
            return (element.id() == null || idMatches(element.id(), node))
                    && (element.label() == null || same(node.label(), element.label()))
                    && (element.text() == null || same(node.text(), element.text()))
                    && (element.holding() == null || holds(node, element.holding()));
        }

        private static boolean holds(UiNode node, Words words) {
            for (UiNode child : node.children()) {
                if (same(child.text(), words) || holds(child, words)) return true;
            }
            return false;
        }

        /**
         * Of nested holders, the ones a person taps: a holder inside a tappable holder goes (the row's wrapper),
         * then every holder another one is inside (the list and the window around the row).
         */
        private List<UiNode> tapped(List<UiNode> hits) {
            Set<UiNode> all = identitySet(hits);
            List<UiNode> rows = hits.stream()
                    .filter(hit -> !hasAncestor(hit, all, true))
                    .toList();
            Set<UiNode> outer = identitySet(List.of());
            for (UiNode hit : rows) {
                for (UiNode up = parents.get(hit); up != null; up = parents.get(up)) outer.add(up);
            }
            return rows.stream().filter(hit -> !outer.contains(hit)).toList();
        }

        /** Whether one of {@code among} holds {@code node}, a tappable one when {@code tappable}. */
        private boolean hasAncestor(UiNode node, Set<UiNode> among, boolean tappable) {
            for (UiNode up = parents.get(node); up != null; up = parents.get(up)) {
                if (among.contains(up) && (!tappable || up.has(Trait.CLICKABLE))) return true;
            }
            return false;
        }

        private List<UiNode> inside(List<UiNode> hits, List<UiNode> containers) {
            Set<UiNode> around = identitySet(containers);
            return hits.stream().filter(hit -> hasAncestor(hit, around, false)).toList();
        }

        private List<UiNode> below(List<UiNode> hits, List<UiNode> neighbours) {
            Set<UiNode> nearest = identitySet(List.of());
            for (UiNode neighbour : neighbours) {
                Rectangle above = neighbour.bounds();
                UiNode best = null;
                int gap = Integer.MAX_VALUE;
                long area = Long.MAX_VALUE;
                for (UiNode hit : hits) {
                    Rectangle b = hit.bounds();
                    int g = b.y - (above.y + above.height);
                    long a = (long) b.width * b.height;
                    if (hit == neighbour || g < 0 || !overlapsAcross(b, above)) continue;
                    if (g < gap || (g == gap && a < area)) {
                        best = hit;
                        gap = g;
                        area = a;
                    }
                }
                if (best != null) nearest.add(best);
            }
            return hits.stream().filter(nearest::contains).toList();
        }
    }

    private static Set<UiNode> identitySet(List<UiNode> nodes) {
        Set<UiNode> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(nodes);
        return set;
    }

    /** Whether {@code a} and {@code b} share some of their width. */
    public static boolean overlapsAcross(Rectangle a, Rectangle b) {
        return a.x < b.x + b.width && b.x < a.x + a.width;
    }

    /**
     * The {@code id} clue that finds {@code node}: its id without the package when that is its app's or
     * {@code android}, else its whole id; null when it has none.
     */
    public static String idClue(UiNode node) {
        if (node.id().isEmpty()) return null;
        String shortId = node.shortId();
        return idMatches(shortId, node) ? shortId : node.id();
    }

    static boolean idMatches(String id, UiNode node) {
        // A WebView's HTML id has no package at all.
        if (id.contains(":id/") || !node.id().contains(":id/")) return node.id().equals(id);
        if (!node.shortId().equals(id)) return false;
        String prefix = node.id().substring(0, node.id().length() - id.length());
        return prefix.equals("android:id/") || prefix.equals(node.packageName() + ":id/");
    }

    private static boolean same(String said, Words words) {
        return !said.isEmpty() && LineMatch.sameText(said, words.text());
    }

    /** Where {@code node} is on the frame its tree was read for: the screen's pixels, as a frame's. */
    public static Rect place(UiNode node) {
        Rectangle b = node.bounds();
        return new Rect(b.x, b.y, b.width, b.height);
    }

    /** What {@code node} says of itself: greyed out when not enabled, picked when selected or checked. */
    public static State state(UiNode node) {
        if (!node.has(Trait.ENABLED)) return State.DISABLED;
        return node.has(Trait.SELECTED) || node.has(Trait.CHECKED) ? State.SELECTED : State.NORMAL;
    }

    /**
     * What a run's error calls {@code element}: the name of its picture's file, which a pick names after its constant
     * ({@code elements/battery.png} → {@code battery}), else its words or its id.
     */
    public static String named(Element element) {
        if (element.shot() != null) {
            String file = element.shot().substring(element.shot().lastIndexOf('/') + 1);
            int dot = file.lastIndexOf('.');
            return dot > 0 ? file.substring(0, dot) : file;
        }
        return "element " + element.shown();
    }
}
