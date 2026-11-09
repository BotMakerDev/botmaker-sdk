package com.botmaker.sdk.plugin.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The map's groups, worked out from its ways alone (spec 2026-11-09 § Groups): screens that link to each other more
 * than to the rest. Louvain modularity over the ways taken undirected, a pair weighing as many ways as join it, nodes
 * visited in the map's order so the same map always gives the same groups. A group of one screen is no group.
 */
final class ScreenClusters {

    /** One group: {@code name} is its screen with the most ways, {@code screens} its members in the map's order. */
    record Group(String name, List<String> screens) {

        Group {
            screens = List.copyOf(screens);
        }
    }

    /** A move must gain more than this to be made: rounding never moves a screen back and forth. */
    private static final double EPSILON = 1e-12;

    private ScreenClusters() {}

    /**
     * The groups of {@code screens} by {@code ways}, in the order of their first member. A way to or from a screen not
     * in {@code screens}, or from a screen to itself, counts for nothing.
     */
    static List<Group> of(List<String> screens, List<ElkLayout.Way> ways) {
        int n = screens.size();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.putIfAbsent(screens.get(i), i);
        double[][] weight = new double[n][n];
        int[] degree = new int[n];
        for (ElkLayout.Way way : ways) {
            Integer a = index.get(way.from());
            Integer b = index.get(way.to());
            if (a == null || b == null || a.equals(b)) continue;
            weight[a][b]++;
            weight[b][a]++;
            degree[a]++;
            degree[b]++;
        }
        // Each screen's group, by index; refined level by level, each level's groups being the next level's nodes.
        int[] groupOf = new int[n];
        for (int i = 0; i < n; i++) groupOf[i] = i;
        double[][] level = weight;
        while (true) {
            int[] moved = oneLevel(level);
            int count = 0;
            for (int g : moved) count = Math.max(count, g + 1);
            if (count == level.length) break;
            for (int i = 0; i < n; i++) groupOf[i] = moved[groupOf[i]];
            level = folded(level, moved, count);
        }

        Map<Integer, List<Integer>> members = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) members.computeIfAbsent(groupOf[i], g -> new ArrayList<>()).add(i);
        List<Group> groups = new ArrayList<>();
        for (List<Integer> group : members.values()) {
            if (group.size() < 2) continue;
            int busiest = group.getFirst();
            for (int i : group) if (degree[i] > degree[busiest]) busiest = i;
            groups.add(new Group(screens.get(busiest), group.stream().map(screens::get).toList()));
        }
        return groups;
    }

    /**
     * Louvain's first phase over {@code weight}: each node in turn moves to the neighbouring group that gains the most
     * modularity, until a pass moves none. Answers each node's group, numbered by first member from 0.
     */
    private static int[] oneLevel(double[][] weight) {
        int n = weight.length;
        double[] degree = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) degree[i] += weight[i][j];
            total += degree[i];
        }
        int[] group = new int[n];
        double[] tot = new double[n];
        for (int i = 0; i < n; i++) {
            group[i] = i;
            tot[i] = degree[i];
        }
        if (total == 0) return group;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < n; i++) {
                int was = group[i];
                tot[was] -= degree[i];
                double[] toGroup = new double[n];
                for (int j = 0; j < n; j++) if (j != i && weight[i][j] > 0) toGroup[group[j]] += weight[i][j];
                int best = was;
                double bestGain = toGroup[was] - tot[was] * degree[i] / total;
                for (int j = 0; j < n; j++) {
                    int g = group[j];
                    if (j == i || weight[i][j] <= 0 || g == best) continue;
                    double gain = toGroup[g] - tot[g] * degree[i] / total;
                    if (gain > bestGain + EPSILON) {
                        best = g;
                        bestGain = gain;
                    }
                }
                group[i] = best;
                tot[best] += degree[i];
                if (best != was) changed = true;
            }
        }
        Map<Integer, Integer> renumbered = new HashMap<>();
        int[] numbered = new int[n];
        for (int i = 0; i < n; i++) numbered[i] = renumbered.computeIfAbsent(group[i], g -> renumbered.size());
        return numbered;
    }

    /** {@code weight} with each group of {@code group} made one node: the weights between groups summed. */
    private static double[][] folded(double[][] weight, int[] group, int count) {
        double[][] folded = new double[count][count];
        for (int i = 0; i < weight.length; i++) {
            for (int j = 0; j < weight.length; j++) folded[group[i]][group[j]] += weight[i][j];
        }
        return folded;
    }
}
