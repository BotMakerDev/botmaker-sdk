package com.botmaker.sdk.internal.screens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the map's screens are told by, set beside set, before any shot is read: two screens told by the same parts
 * cannot be told apart, and a screen whose parts all show on another, with none of that other's parts absent on it, is
 * taken for it there unless the other ranks first. Each line says which part to add. A part two screens share is no
 * problem on its own: the set tells a screen, never one part (2026-11-02).
 */
public final class MarkerSets {

    private MarkerSets() {}

    /** One line per pair of screens whose sets cannot tell them apart, {@code only} alone when not null. */
    public static List<String> problems(ScreenGraph graph, String only) {
        List<String> lines = new ArrayList<>();
        // Each set built once: a map of a hundred screens is ten thousand pairs.
        Map<String, Markers> sets = new LinkedHashMap<>();
        graph.screens().forEach((c, screen) -> {
            Markers set = graph.markers(c);
            if (!screen.isPopup() && !set.keys().isEmpty()) sets.put(c, set);
        });
        List<String> constants = List.copyOf(sets.keySet());
        for (int i = 0; i < constants.size(); i++) {
            for (int j = 0; j < constants.size(); j++) {
                if (i == j) continue;
                String a = constants.get(i);
                String b = constants.get(j);
                if (only != null && !only.equals(a) && !only.equals(b)) continue;
                problem(graph, a, sets.get(a), b, sets.get(b), i < j).ifPresent(lines::add);
            }
        }
        return lines;
    }

    /**
     * Why {@code a} is not told apart from {@code b}: the same set (said once, for {@code first}), or {@code a}'s set
     * inside {@code b}'s with nothing between them. A panel or tab of {@code a} shows over it by design and is skipped.
     * Two equal sets one of whose absent parts tells them apart are left to it.
     */
    private static Optional<String> problem(ScreenGraph graph, String a, Markers setA, String b, Markers setB,
                                            boolean first) {
        if (shownOver(graph, b, a) || shownOver(graph, a, b)) return Optional.empty();
        Set<String> keysA = setA.keys();
        Set<String> keysB = setB.keys();
        if (keysA.equals(keysB) && setA.absentKeys().equals(setB.absentKeys())) {
            if (!first) return Optional.empty();
            String own = ownPicture(graph, a, b).map(p -> ": mark one by a part only it has (" + p + ")")
                    .orElse(": mark one by a part only it has");
            return Optional.of(graph.shown(a) + " and " + graph.shown(b) + " are told by the same parts ("
                    + String.join(" + ", keysA) + "), so a run cannot tell them apart" + own + ".");
        }
        if (keysB.containsAll(keysA) && keysB.size() > keysA.size()
                && setA.absentKeys().stream().noneMatch(keysB::contains)) {
            String add = keysB.stream().filter(k -> !keysA.contains(k)).findFirst().orElseThrow();
            return Optional.of(graph.shown(a) + ": every part telling it shows on " + graph.shown(b)
                    + " too, which a run tells only by ranking the larger set first: make " + add + " absent on "
                    + graph.shown(a) + " to tell them apart.");
        }
        return Optional.empty();
    }

    /** Whether {@code over} is a panel or a tab of {@code under}, shown over it. */
    private static boolean shownOver(ScreenGraph graph, String over, String under) {
        return graph.panelOf(over).filter(under::equals).isPresent()
                || graph.tabParent(over).filter(under::equals).isPresent();
    }

    /** A picture {@code a} names that {@code b} does not: one of its own a part could mark it by. */
    private static Optional<String> ownPicture(ScreenGraph graph, String a, String b) {
        Set<String> theirs = new LinkedHashSet<>(graph.pictures(b));
        return graph.pictures(a).stream().filter(p -> !theirs.contains(p)).filter(Objects::nonNull).findFirst();
    }
}
