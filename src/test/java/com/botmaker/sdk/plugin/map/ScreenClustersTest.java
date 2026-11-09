package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.plugin.map.ElkLayout.Way;
import com.botmaker.sdk.plugin.map.ScreenClusters.Group;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenClustersTest {

    private static Way way(String from, String to) {
        return new Way(from, to, false, false);
    }

    @Test
    void twoTrianglesJoinedByOneWayAreTwoGroups() {
        List<String> screens = List.of("A", "B", "C", "D", "E", "F");
        // A second way between A and B, and between D and E: D has the most ways of its triangle.
        List<Way> ways = List.of(way("A", "B"), way("B", "C"), way("C", "A"), way("A", "B"),
                way("D", "E"), way("E", "F"), way("F", "D"), way("E", "D"), way("C", "D"));
        List<Group> groups = ScreenClusters.of(screens, ways);
        assertEquals(2, groups.size(), groups.toString());
        assertEquals(List.of("A", "B", "C"), groups.get(0).screens());
        assertEquals(List.of("D", "E", "F"), groups.get(1).screens());
        assertEquals("A", groups.get(0).name(), "A and B have three ways each: the first wins");
        assertEquals("D", groups.get(1).name(), "D has four ways");
    }

    @Test
    void oneCliqueIsOneGroup() {
        List<String> screens = List.of("A", "B", "C", "D");
        List<Way> ways = new ArrayList<>();
        for (String a : screens) for (String b : screens) if (!a.equals(b)) ways.add(way(a, b));
        List<Group> groups = ScreenClusters.of(screens, ways);
        assertTrue(groups.size() <= 1, groups.toString());
        if (!groups.isEmpty()) assertEquals(screens, groups.getFirst().screens());
    }

    @Test
    void noWaysNoGroups() {
        assertEquals(List.of(), ScreenClusters.of(List.of("A", "B", "C"), List.of()));
        assertEquals(List.of(), ScreenClusters.of(List.of(), List.of()));
    }

    @Test
    void sameInputSameGroups() {
        List<String> screens = List.of("A", "B", "C", "D", "E", "F", "G");
        List<Way> ways = List.of(way("A", "B"), way("B", "C"), way("C", "A"), way("D", "E"), way("E", "F"),
                way("F", "D"), way("C", "D"), way("G", "A"), way("G", "F"));
        assertEquals(ScreenClusters.of(screens, ways), ScreenClusters.of(screens, ways));
    }
}
