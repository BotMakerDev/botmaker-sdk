package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.toolkit.graph.GraphLayout;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Box;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.sdk.plugin.map.ElkLayout.Way;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElkLayoutTest {

    private static final List<Card> CARDS = List.of(Card.of("HOME", 160, 120), Card.of("SHOP", 140, 90),
            Card.of("BAG", 120, 80), Card.of("MAIL", 120, 100), Card.of("REWARD", 100, 60), Card.of("LONE", 100, 60));
    /** Home's tree, two ways back to it, a way across, a second way to the shop, a popup and a screen nothing reaches. */
    private static final List<Way> WAYS = List.of(new Way("BAG", "HOME", false, true),
            new Way("HOME", "SHOP", true, false), new Way("SHOP", "BAG", true, false),
            new Way("HOME", "MAIL", true, false), new Way("SHOP", "HOME", false, true),
            new Way("HOME", "BAG", false, false), new Way("HOME", "SHOP", false, false),
            new Way("SHOP", "REWARD", true, false));
    private static final Gaps GAPS = new Gaps(90, 28);

    private static Placement laid(Direction direction) {
        return ElkLayout.arrange(LayoutMode.LAYERED, CARDS, WAYS, Set.of("REWARD"), "HOME", direction, GAPS);
    }

    private static Placement laid(LayoutMode mode, Direction direction) {
        return ElkLayout.arrange(mode, CARDS, WAYS, Set.of("REWARD"), "HOME", direction, GAPS);
    }

    @Test
    void everyModeLaysOutOneScreen() {
        for (LayoutMode mode : LayoutMode.values()) {
            Placement placed = ElkLayout.arrange(mode, List.of(Card.of("HOME", 100, 60)), List.of(), Set.of(), "HOME",
                    Direction.RIGHT, GAPS);
            assertEquals(Set.of("HOME"), placed.at().keySet(), mode.id());
        }
    }

    @Test
    void everyModePlacesEveryCard() {
        for (LayoutMode mode : LayoutMode.values()) {
            for (Direction direction : Direction.values()) {
                Placement placed = laid(mode, direction);
                assertEquals(CARDS.stream().map(Card::id).collect(Collectors.toSet()), placed.at().keySet(),
                        mode.id());
                for (Card a : CARDS) {
                    for (Card b : CARDS) {
                        if (a != b) {
                            assertFalse(box(placed, a.id()).overlaps(box(placed, b.id())),
                                    mode.id() + ": " + a.id() + " on " + b.id());
                        }
                    }
                }
            }
        }
    }

    @Test
    void treeRootIsOnTop() {
        Placement placed = laid(LayoutMode.TREE, Direction.DOWN);
        for (String other : List.of("SHOP", "BAG", "MAIL")) {
            assertTrue(placed.at().get("HOME").y() < placed.at().get(other).y(), other);
        }
    }

    @Test
    void hubCentreIsTheBusiestScreen() {
        List<String> order = CARDS.stream().map(Card::id).toList();
        assertEquals("HOME", ElkLayout.busiest(order, WAYS));
        Placement placed = laid(LayoutMode.HUB, Direction.RIGHT);
        Pt home = box(placed, "HOME").centre();
        // The centre sits inside the box of its ring: SHOP and MAIL, its neighbours, lie on either side of it.
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (String ring : List.of("SHOP", "MAIL", "BAG")) {
            Pt c = box(placed, ring).centre();
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minY = Math.min(minY, c.y());
            maxY = Math.max(maxY, c.y());
        }
        assertTrue(home.x() > minX - 0.5 && home.x() < maxX + 0.5 || home.y() > minY - 0.5 && home.y() < maxY + 0.5,
                "the hub is among its ring");
    }

    @Test
    void treeHangsAScreenUnderItsEntranceNotAnEarlierWay() {
        // HOME reaches BAG first by a way on, but BAG's entrance is from SHOP.
        Map<String, String> parents = ElkLayout.tree("HOME", List.of("HOME", "SHOP", "BAG"), List.of(
                List.of(new Way("HOME", "SHOP", true, false), new Way("SHOP", "BAG", true, false)),
                List.of(new Way("HOME", "BAG", false, false))), false);
        assertEquals(Map.of("SHOP", "HOME", "BAG", "SHOP"), parents);
    }

    @Test
    void treeReachesByOtherWaysWhatNoEntranceReaches() {
        Map<String, String> parents = ElkLayout.tree("HOME", List.of("HOME", "SHOP", "BAG"), List.of(
                List.of(new Way("SHOP", "BAG", true, false)),
                List.of(new Way("HOME", "SHOP", false, false))), false);
        assertEquals(Map.of("SHOP", "HOME", "BAG", "SHOP"), parents);
    }

    @Test
    void hubRingHoldsTwelveScreensSideBySide() {
        List<Card> cards = new ArrayList<>(List.of(Card.of("HUB", 160, 120)));
        List<Way> ways = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            cards.add(Card.of("S" + i, 160, 120));
            ways.add(new Way("HUB", "S" + i, true, false));
        }
        Placement placed = ElkLayout.arrange(LayoutMode.HUB, cards, ways, Set.of(), "HUB", Direction.RIGHT, GAPS);
        for (Card a : cards) {
            for (Card b : cards) {
                if (a != b) {
                    assertFalse(a.at(placed.at().get(a.id())).overlaps(b.at(placed.at().get(b.id()))),
                            a.id() + " on " + b.id());
                }
            }
        }
    }

    @Test
    void sameMapSameLayout() {
        for (LayoutMode mode : LayoutMode.values()) {
            assertEquals(laid(mode, Direction.RIGHT).at(), laid(mode, Direction.RIGHT).at(), mode.id());
        }
    }

    private static Box box(Placement placed, String id) {
        Card card = CARDS.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        return card.at(placed.at().get(id));
    }

    private static boolean onBorder(Box box, Pt p) {
        boolean near = p.x() >= box.minX() - 0.5 && p.x() <= box.maxX() + 0.5 && p.y() >= box.minY() - 0.5
                && p.y() <= box.maxY() + 0.5;
        return near && !box.holds(p, 0.5);
    }

    @Test
    void theEntrancesSetTheRanksFromTheFirstScreenEitherWay() {
        Placement right = laid(Direction.RIGHT);
        assertTrue(right.at().get("HOME").x() < right.at().get("SHOP").x());
        assertTrue(right.at().get("SHOP").x() < right.at().get("BAG").x(), "a way back pulls nothing forward");
        assertTrue(right.at().get("HOME").x() < right.at().get("MAIL").x());

        Placement down = laid(Direction.DOWN);
        assertTrue(down.at().get("HOME").y() < down.at().get("SHOP").y());
        assertTrue(down.at().get("SHOP").y() < down.at().get("BAG").y());
    }

    @Test
    void noCardsOverlapAndEveryPathRunsBorderToBorderClearOfTheOthers() {
        Placement placed = laid(Direction.RIGHT);
        for (Card a : CARDS) {
            for (Card b : CARDS) {
                if (a != b) assertFalse(box(placed, a.id()).overlaps(box(placed, b.id())), a.id() + " on " + b.id());
            }
        }
        for (Way way : WAYS) {
            if (way.to().equals("REWARD")) continue;
            int nth = (int) WAYS.subList(0, WAYS.indexOf(way)).stream()
                    .filter(w -> w.from().equals(way.from()) && w.to().equals(way.to())).count();
            List<Pt> path = placed.path(GraphLayout.Link.of(way.from(), way.to()), nth);
            assertTrue(path.size() >= 2, way + " has a path");
            assertTrue(onBorder(box(placed, way.from()), path.getFirst()), way + " starts on its screen");
            assertTrue(onBorder(box(placed, way.to()), path.getLast()), way + " ends on the screen it opens");
            for (int i = 1; i < path.size(); i++) {
                Pt a = path.get(i - 1), b = path.get(i);
                assertTrue(Math.abs(a.x() - b.x()) < 0.5 || Math.abs(a.y() - b.y()) < 0.5, way + " runs square");
                for (Card other : CARDS) {
                    if (other.id().equals(way.from()) || other.id().equals(way.to())) continue;
                    for (double t = 0; t <= 1; t += 0.05) {
                        Pt p = new Pt(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
                        assertFalse(box(placed, other.id()).holds(p, 1), way + " crosses " + other.id());
                    }
                }
            }
        }
    }

    @Test
    void twoWaysTheSameWayHaveTwoPathsAndAPopupsNone() {
        Placement placed = laid(Direction.RIGHT);
        GraphLayout.Link toShop = GraphLayout.Link.of("HOME", "SHOP");
        assertEquals(2, placed.paths().get(toShop).size());
        assertNotEquals(placed.path(toShop, 0), placed.path(toShop, 1));
        assertTrue(placed.path(GraphLayout.Link.of("SHOP", "REWARD"), 0).isEmpty(), "a popup's link is the canvas's");
        assertEquals(CARDS.stream().map(Card::id).collect(Collectors.toSet()), placed.at().keySet(),
                "popups and unreached screens have a place too");
    }
}
