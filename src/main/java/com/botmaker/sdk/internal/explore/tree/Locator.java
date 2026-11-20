// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore.tree;

import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Role;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import com.botmaker.shared.web.WebTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * How a part of a screen is found, the way a tester finds it (web discovery spec §5): on a website by its role and
 * accessible name, else its test id or DOM id, else the text it holds, else its role and index; in an app by its
 * resource id, else what a screen reader says, else its text. The index is given only when the other clues find more
 * than one node, and says which, as {@link ElementMatch} counts them.
 *
 * <p>Plain data, so a run's answer carries it whole; the bot's {@link Element} is written from it with the bot's own
 * {@code Texts} by the side that applies the answer.
 *
 * @param role  a website's part's role; null in an app
 * @param name  its accessible name, an app's label; null when not a clue
 * @param id    its test id or DOM id, an app's resource id; null when not a clue
 * @param text  its own text; null when not a clue
 * @param index which of what the other clues find, from 0; null when they find it alone
 */
public record Locator(Role role, String name, String id, String text, Integer index) {

    public Locator {
        name = blankToNull(name);
        id = blankToNull(id);
        text = blankToNull(text);
        if (role == Role.UNKNOWN) role = null;
    }

    /** A part found and the node it is in the tree it was read from. */
    public record Located(Locator locator, UiNode node) {}

    /** The element the bot finds it by, with plain words for its texts. */
    public Element element() {
        Words label = name == null ? null : Words.of(name);
        Words own = text == null ? null : Words.of(text);
        return new Element(id, label, own, null, null, false, false, null, null, index, null, role);
    }

    /** The locator as a tester reads it: {@code button "Get started"}, {@code #email}, {@code link [2]}. */
    public String line() {
        StringBuilder line = new StringBuilder();
        if (role != null) line.append(role.id());
        if (name != null) line.append(line.isEmpty() ? "" : " ").append('"').append(name).append('"');
        if (id != null) line.append(line.isEmpty() ? "" : " ").append('#').append(id);
        if (text != null) line.append(line.isEmpty() ? "" : " ").append("text \"").append(text).append('"');
        if (index != null) line.append(" [").append(index).append(']');
        return line.toString();
    }

    /**
     * The parts a person acts on in {@code tree}, in its order, each once by its locator. Look-alike parts (three
     * <em>Read more</em> links) are each kept, told apart by their index: they may lead to different pages.
     */
    public static List<Located> inputs(UiTree tree) {
        ElementMatch.Matcher matcher = ElementMatch.in(tree);
        Set<Locator> seen = new HashSet<>();
        List<Located> found = new ArrayList<>();
        for (UiNode node : matcher.nodes()) {
            if (!acted(node) || folded(node, matcher)) continue;
            Optional<Locator> locator = of(node, matcher);
            if (locator.isPresent() && seen.add(locator.get())) found.add(new Located(locator.get(), node));
        }
        return List.copyOf(found);
    }

    /**
     * {@code node}'s locator in the tree {@code matcher} reads, or empty when nothing about it is a clue, or when its
     * clues do not find it back.
     */
    public static Optional<Locator> of(UiNode node, ElementMatch.Matcher matcher) {
        return bare(node).flatMap(bare -> placed(bare, node, matcher));
    }

    /**
     * Whether {@code node} is one control with another of its name, nested in or around it: a menu's button that
     * holds its link is pressed once, not twice for the same page. The link is kept, it says where it goes;
     * otherwise the inner one.
     */
    static boolean folded(UiNode node, ElementMatch.Matcher matcher) {
        String name = node.label().strip();
        if (name.isEmpty()) return false;
        for (UiNode up = matcher.parentOf(node); up != null; up = matcher.parentOf(up)) {
            if (acted(up) && up.label().strip().equalsIgnoreCase(name)) {
                // An inner link under an outer non-link is kept, its holder folded; any other pair keeps the inner.
                return link(up) && !link(node);
            }
        }
        return twin(node.children(), name, link(node));
    }

    /** Whether a control named {@code name} below {@code nodes} folds {@code outer}, a link or not. */
    private static boolean twin(List<UiNode> nodes, String name, boolean outerLink) {
        for (UiNode child : nodes) {
            if (acted(child) && child.label().strip().equalsIgnoreCase(name) && (link(child) || !outerLink)) {
                return true;
            }
            if (twin(child.children(), name, outerLink)) return true;
        }
        return false;
    }

    private static boolean link(UiNode node) {
        return WebTree.roleOf(node.className()).map(r -> Role.fromId(r.id()) == Role.LINK).orElse(false);
    }

    /** Whether a person acts on {@code node}: a website's control, or an app's node it taps or types into. */
    static boolean acted(UiNode node) {
        // A website's role is shared's, whose interactive() the SDK's own Role mirror does not carry.
        if (WebTree.roleOf(node.className()).isPresent()) return node.has(Trait.CLICKABLE);
        return node.has(Trait.CLICKABLE) || node.className().endsWith("EditText");
    }

    /**
     * The clues before any index: the first the spec's order offers. A field's text is what was typed into it, never
     * a clue: a field with nothing else is found by its role and index on a website, and not at all in an app.
     */
    private static Optional<Locator> bare(UiNode node) {
        Optional<com.botmaker.shared.web.Role> webRole = WebTree.roleOf(node.className());
        if (webRole.isPresent()) {
            Role role = Role.fromId(webRole.get().id());
            if (role != Role.UNKNOWN && !node.label().isBlank()) {
                return Optional.of(new Locator(role, node.label(), null, null, null));
            }
            if (!node.id().isBlank()) return Optional.of(new Locator(null, null, node.id(), null, null));
            if (!WebTree.field(webRole.get()) && !node.text().isBlank()) {
                return Optional.of(new Locator(null, null, null, node.text(), null));
            }
            return role == Role.UNKNOWN ? Optional.empty() : Optional.of(new Locator(role, null, null, null, null));
        }
        if (!node.shortId().isBlank()) return Optional.of(new Locator(null, null, node.shortId(), null, null));
        if (!node.label().isBlank()) return Optional.of(new Locator(null, node.label(), null, null, null));
        if (!node.className().endsWith("EditText") && !node.text().isBlank()) {
            return Optional.of(new Locator(null, null, null, node.text(), null));
        }
        return Optional.empty();
    }

    /**
     * {@code bare} with the index of {@code node} among what it finds when it finds more than one; empty when it does
     * not find {@code node} at all, so no locator points at another part.
     */
    private static Optional<Locator> placed(Locator bare, UiNode node, ElementMatch.Matcher matcher) {
        List<UiNode> hits = matcher.matches(bare.element());
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i) != node) continue;
            return Optional.of(hits.size() == 1 ? bare : new Locator(bare.role, bare.name, bare.id, bare.text, i));
        }
        return Optional.empty();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
