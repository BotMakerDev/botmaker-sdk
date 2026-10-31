package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Names an input picked on a frozen tree with the fewest clues that find only it (spec § Naming at pick time). It
 * tries, in turn, and keeps the first that {@link ElementMatch} finds once: its id alone; its id and its words (its
 * text, else its label, else the text it holds); those inside its nearest scrolling or identified container; those
 * below a neighbour. Past that, a copy of a list's row is a repeated input, and anything else is found by its index,
 * which breaks when the list changes.
 */
public final class ElementNames {

    /** How many of the nodes above an input are looked at as its {@code below} neighbour, nearest first. */
    private static final int NEIGHBOURS = 16;

    private ElementNames() {}

    /**
     * How an input was named.
     *
     * @param element  the clues that find it
     * @param repeated whether it is one of copies the screen shows, every one with the same clues: a repeated
     *                 input, which a body picks among
     * @param byText   whether a clue is words, which a translated app shows otherwise
     * @param byIndex  whether it is told from the rest by its place only
     */
    public record Named(Element element, boolean repeated, boolean byText, boolean byIndex) {}

    /**
     * {@code picked}'s name on {@code tree}, the app's windows as matching reads them. {@code texts} gives the bot's
     * {@code Texts} constant for a text, a new one when it has none; it is asked only for the words of the name
     * found. Empty when it has no id, label or text of its own or inside it, or holds only what a node inside it
     * holds too: a picture is cut for it instead.
     */
    public static Optional<Named> name(UiNode picked, UiTree tree, Function<String, Words> texts) {
        return name(picked, tree).map(named -> new Named(inTexts(named.element(), texts), named.repeated(),
                named.byText(), named.byIndex()));
    }

    private static Optional<Named> name(UiNode picked, UiTree tree) {
        Namer namer = new Namer(ElementMatch.in(tree));
        Optional<Element> unique = namer.unique(picked);
        if (unique.isPresent()) return Optional.of(named(unique.get(), false, false));
        Optional<Element> widest = namer.widest(picked);
        if (widest.isEmpty()) return Optional.empty();
        Optional<Element> below = namer.below(picked, widest.get());
        if (below.isPresent()) return Optional.of(named(below.get(), false, false));
        List<UiNode> found = namer.matcher.matches(widest.get());
        int index = indexOf(found, picked);
        // A window around a row holds the row's words too, and matching keeps the row.
        if (index < 0) return Optional.empty();
        if (found.stream().allMatch(other -> namer.copies(picked, other))) {
            return Optional.of(named(widest.get(), true, false));
        }
        return Optional.of(named(widest.get().index(index), false, true));
    }

    private static Named named(Element element, boolean repeated, boolean byIndex) {
        return new Named(element, repeated, byText(element), byIndex);
    }

    private static boolean byText(Element element) {
        if (element == null) return false;
        return hasWords(element) || byText(element.inside()) || byText(element.below());
    }

    /** {@code element} with its words, and its anchors', the bot's {@code Texts} constants. */
    private static Element inTexts(Element element, Function<String, Words> texts) {
        if (element == null) return null;
        Function<Words, Words> constant = words -> words == null ? null : texts.apply(words.text());
        return new Element(element.id(), constant.apply(element.label()), constant.apply(element.text()),
                constant.apply(element.holding()), inTexts(element.inside(), texts),
                inTexts(element.below(), texts), element.index(), element.shot());
    }

    private static int indexOf(List<UiNode> nodes, UiNode node) {
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i) == node) return i;
        return -1;
    }

    /** One tree's matcher and the names already found in it; words are plain until the name is chosen. */
    private static final class Namer {
        private final ElementMatch.Matcher matcher;
        private final Map<UiNode, Optional<Element>> unique = new IdentityHashMap<>();

        Namer(ElementMatch.Matcher matcher) {
            this.matcher = matcher;
        }

        /** The first of the id, the id and words, and those inside a container that finds only {@code node}. */
        Optional<Element> unique(UiNode node) {
            Optional<Element> known = unique.get(node);
            if (known != null) return known;
            Optional<Element> name = Optional.empty();
            for (Element tried : rungs(node)) {
                if (findsOnly(tried, node)) {
                    name = Optional.of(tried);
                    break;
                }
            }
            unique.put(node, name);
            return name;
        }

        /** The most clues {@code node} gets before a neighbour: its own, inside its nearest named container. */
        Optional<Element> widest(UiNode node) {
            Optional<Element> own = own(node);
            if (own.isEmpty()) return own;
            for (UiNode up = matcher.parentOf(node); up != null; up = matcher.parentOf(up)) {
                if (!container(up)) continue;
                Optional<Element> container = unique(up);
                if (container.isPresent()) return Optional.of(own.get().inside(container.get()));
            }
            return own;
        }

        /** {@code widest} below one of the nearest nodes above {@code node}, when that finds only it. */
        Optional<Element> below(UiNode node, Element widest) {
            Rectangle at = node.bounds();
            List<UiNode> above = matcher.nodes().stream()
                    .filter(other -> other != node)
                    .filter(other -> {
                        Rectangle b = other.bounds();
                        return b.y + b.height <= at.y && ElementMatch.overlapsAcross(b, at);
                    })
                    .sorted(Comparator.comparingInt(other -> at.y - bottom(other)))
                    .limit(NEIGHBOURS)
                    .toList();
            for (UiNode neighbour : above) {
                Optional<Element> anchor = unique(neighbour);
                if (anchor.isEmpty()) continue;
                Element named = widest.below(anchor.get());
                if (findsOnly(named, node)) return Optional.of(named);
            }
            return Optional.empty();
        }

        /**
         * Whether {@code other} is a copy of {@code node} in a list: the same clues, and, up to where they meet,
         * parents of the same view and id.
         */
        boolean copies(UiNode node, UiNode other) {
            if (!signature(node).equals(signature(other))) return false;
            UiNode a = node;
            UiNode b = other;
            while (a != b) {
                if (a == null || b == null) return false;
                if (!a.className().equals(b.className()) || !a.id().equals(b.id())) return false;
                a = matcher.parentOf(a);
                b = matcher.parentOf(b);
            }
            return true;
        }

        private List<Element> rungs(UiNode node) {
            List<Element> rungs = new ArrayList<>();
            String id = ElementMatch.idClue(node);
            if (id != null) rungs.add(Element.byId(id));
            own(node).filter(own -> own.id() == null || hasWords(own)).ifPresent(rungs::add);
            widest(node).filter(w -> w.inside() != null).ifPresent(rungs::add);
            return rungs;
        }

        /** {@code node}'s own clues: its id and its words. */
        private static Optional<Element> own(UiNode node) {
            String id = ElementMatch.idClue(node);
            Element element = id == null ? null : Element.byId(id);
            if (!node.text().isEmpty()) {
                Words words = Words.of(node.text());
                return Optional.of(element == null ? Element.byText(words) : element.text(words));
            }
            if (!node.label().isEmpty()) {
                Words words = Words.of(node.label());
                return Optional.of(element == null ? Element.byLabel(words) : element.label(words));
            }
            Optional<String> held = held(node);
            if (held.isPresent()) {
                Words words = Words.of(held.get());
                return Optional.of(element == null ? Element.byHolding(words) : element.holding(words));
            }
            return Optional.ofNullable(element);
        }

        private boolean findsOnly(Element element, UiNode node) {
            List<UiNode> found = matcher.matches(element);
            return found.size() == 1 && found.getFirst() == node;
        }

        private static List<String> signature(UiNode node) {
            return List.of(node.id(), node.text(), node.label(), held(node).orElse(""));
        }
    }

    private static boolean hasWords(Element element) {
        return element.label() != null || element.text() != null || element.holding() != null;
    }

    /** A container a name may say an input is inside: a list that scrolls, or a view with an id. */
    private static boolean container(UiNode node) {
        return node.has(Trait.SCROLLABLE) || !node.id().isEmpty();
    }

    /** The first text inside {@code node}, depth first. */
    private static Optional<String> held(UiNode node) {
        for (UiNode child : node.children()) {
            if (!child.text().isEmpty()) return Optional.of(child.text());
            Optional<String> deeper = held(child);
            if (deeper.isPresent()) return deeper;
        }
        return Optional.empty();
    }

    private static int bottom(UiNode node) {
        Rectangle b = node.bounds();
        return b.y + b.height;
    }
}
