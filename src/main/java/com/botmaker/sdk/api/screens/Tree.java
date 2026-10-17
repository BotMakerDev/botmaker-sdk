package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Buttons of a {@link Screen} linked in a tree that scrolls in two dimensions — a game's research or skill tree:
 * {@code Tree.of(List.of(Node.of(Pictures.DAMAGE), Node.of(Pictures.HEALTH).beside(…))).dragged()}.
 *
 * <p>Each node is the picture of one of the screen's {@link Button buttons}, a field of its own, so a body presses it
 * as any other ({@code TREE_I.fistFight}); the tree says how to bring it into view. Nothing places a node on the
 * screen: each is placed {@link Node#beside} a node it is connected to, in that node's size, and a walk follows those
 * offsets from a node in view (the user's call, 2026-10-17). The tree says only how it is scrolled: {@link #dragged
 * by dragging}, {@link #wheel by the wheel}, or neither when it fits on the screen.
 *
 * <p>Its traits are its nodes' defaults, as a {@link Strip}'s are its members'.
 *
 * @param nodes        its nodes, in the order they are written
 * @param isDragged    whether the view is dragged to scroll it
 * @param notches      how far one wheel scroll turns, for a tree the wheel scrolls; 0 when it does not
 * @param isTried      whether Discovery scrolled it
 * @param isLeaving    whether its nodes go to screens mapped already that they are no way into
 * @param isInfo       whether its nodes only tell
 * @param never        why its nodes are never done; null when they may be
 * @param comesAndGoes whether its nodes each show only at times
 * @param lock         the picture proving it locked on this account; null when it is not
 * @param when         when its nodes are there; null when not known
 */
public record Tree(List<Node> nodes, boolean isDragged, int notches, boolean isTried, boolean isLeaving,
                   boolean isInfo, String never, boolean comesAndGoes, ImageTemplate lock, BooleanSupplier when)
        implements Input {

    public Tree {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        comesAndGoes = comesAndGoes || when != null;
    }

    /** One step of a walk from a node to a connected one, by the offset written on either. */
    public record Step(ImageTemplate from, ImageTemplate to, Beside beside, boolean reversed) {}

    /** The buttons {@code nodes}, fitting on the screen until {@link #dragged} or {@link #wheel}. */
    public static Tree of(List<Node> nodes) {
        return new Tree(nodes, false, 0, false, false, false, null, false, null, null);
    }

    /** This tree, scrolled by dragging its view. */
    public Tree dragged() {
        return new Tree(nodes, true, notches, isTried, isLeaving, isInfo, never, comesAndGoes, lock, when);
    }

    /** This tree, scrolled by the wheel, {@code notches} a scroll. */
    public Tree wheel(int notches) {
        return new Tree(nodes, isDragged, notches, isTried, isLeaving, isInfo, never, comesAndGoes, lock, when);
    }

    @Override
    public Tree tried() {
        return new Tree(nodes, isDragged, notches, true, isLeaving, isInfo, never, comesAndGoes, lock, when);
    }

    @Override
    public Tree leaves() {
        return new Tree(nodes, isDragged, notches, isTried, true, isInfo, never, comesAndGoes, lock, when);
    }

    @Override
    public Tree info() {
        return new Tree(nodes, isDragged, notches, isTried, isLeaving, true, never, comesAndGoes, lock, when);
    }

    @Override
    public Tree never(String why) {
        return new Tree(nodes, isDragged, notches, isTried, isLeaving, isInfo, why, comesAndGoes, lock, when);
    }

    @Override
    public Tree sometimes() {
        return new Tree(nodes, isDragged, notches, isTried, isLeaving, isInfo, never, true, lock, when);
    }

    @Override
    public Tree locked(ImageTemplate lock) {
        return new Tree(nodes, isDragged, notches, isTried, isLeaving, isInfo, never, comesAndGoes, lock, when);
    }

    @Override
    public Tree when(BooleanSupplier condition) {
        return new Tree(nodes, isDragged, notches, isTried, isLeaving, isInfo, never, comesAndGoes, lock, condition);
    }

    /** This tree, its nodes {@code nodes}. */
    public Tree nodes(List<Node> nodes) {
        return new Tree(nodes, isDragged, notches, isTried, isLeaving, isInfo, never, comesAndGoes, lock, when);
    }

    /** This tree without the node whose picture is {@code id}, and with no unlock line or offset naming it. */
    public Tree without(String id) {
        List<Node> kept = new ArrayList<>();
        for (Node node : nodes) {
            if (node.picture() != null && node.picture().id().equals(id)) continue;
            kept.add(node.after(node.after().stream().filter(p -> !p.id().equals(id)).toList())
                    .beside(node.beside().stream().filter(b -> b.other() == null || !b.other().id().equals(id))
                            .toList()));
        }
        return nodes(kept);
    }

    /** Whether it scrolls at all: dragged or by the wheel. */
    public boolean scrolls() {
        return isDragged || notches != 0;
    }

    /** The pictures of its nodes, in order. */
    public List<ImageTemplate> members() {
        return nodes.stream().map(Node::picture).toList();
    }

    /** Whether {@code picture} is one of its nodes, by its id. */
    public boolean has(ImageTemplate picture) {
        return node(picture).isPresent();
    }

    /** The node whose picture is {@code picture}, by its id. */
    public Optional<Node> node(ImageTemplate picture) {
        if (picture == null) return Optional.empty();
        return nodes.stream().filter(n -> n.picture() != null && n.picture().id().equals(picture.id())).findFirst();
    }

    /**
     * The fewest steps from any node of {@code from} (ids of nodes in view) to {@code to}, each along one
     * {@link Node#beside} offset read either way; empty when no chain of offsets joins them, or {@code to} is in
     * {@code from}.
     */
    public List<Step> path(Set<String> from, String to) {
        Map<String, List<Step>> edges = new LinkedHashMap<>();
        for (Node node : nodes) {
            if (node.picture() == null) continue;
            for (Beside b : node.beside()) {
                if (b.other() == null) continue;
                edges.computeIfAbsent(b.other().id(), k -> new ArrayList<>())
                        .add(new Step(b.other(), node.picture(), b, false));
                edges.computeIfAbsent(node.picture().id(), k -> new ArrayList<>())
                        .add(new Step(node.picture(), b.other(), b, true));
            }
        }
        Map<String, Step> cameBy = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        List<String> starts = new ArrayList<>(nodes.stream().map(Node::picture)
                .filter(p -> p != null && from.contains(p.id())).map(ImageTemplate::id).toList());
        from.stream().filter(id -> !starts.contains(id)).sorted().forEach(starts::add);
        Set<String> seen = new HashSet<>(starts);
        if (seen.contains(to)) return List.of();
        queue.addAll(starts);
        while (!queue.isEmpty()) {
            String at = queue.removeFirst();
            for (Step step : edges.getOrDefault(at, List.of())) {
                String next = step.to().id();
                if (!seen.add(next)) continue;
                cameBy.put(next, step);
                if (next.equals(to)) {
                    List<Step> path = new ArrayList<>();
                    for (String id = to; cameBy.containsKey(id); id = cameBy.get(id).from().id())
                        path.addFirst(cameBy.get(id));
                    return List.copyOf(path);
                }
                queue.addLast(next);
            }
        }
        return List.of();
    }

    @Override
    public String label() {
        return "tree " + (nodes.isEmpty() || nodes.getFirst().picture() == null ? "?"
                : nodes.getFirst().picture().id());
    }
}
