package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Box;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.Routes;
import com.botmaker.sdk.plugin.flow.FlowArrange.Card;
import com.botmaker.sdk.plugin.flow.FlowArrange.Layout;
import com.botmaker.sdk.plugin.flow.FlowArrange.Route;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Auto-arrange and the wires' paths give a readable picture, whatever the flow's shape. Pure — no JavaFX.
 *
 * <p>Until 2026-10-12 a wire skipping columns ran through the cards between its ends, a wire back looped through
 * the cards it passed, a long flow ran off a fixed 3000×2000 canvas, and Recenter showed only what fit at 1:1.
 * Each case below is a shape that broke one of those; each check is one thing a reader needs.
 */
class FlowArrangeTest {

    /** A flow under test: its cards (sizes and ports) and its wires. */
    private static final class Flow {
        final Map<String, Card> cards = new LinkedHashMap<>();
        final List<Arrow> arrows = new ArrayList<>();

        /** A card with {@code outcomes} ports, sized like the canvas's: taller with more ports. */
        Flow card(String name, int outcomes) {
            Map<String, Double> ports = new LinkedHashMap<>();
            for (int i = 0; i < outcomes; i++) ports.put(port(i), 44.0 + 20 * i);
            double width = 200 + 7 * (name.length() % 5) + 10 * outcomes;
            cards.put(name, new Card(name, 0, 0, width, 50 + 20 * outcomes, ports));
            return this;
        }

        Flow wire(String from, int outcome, String to) {
            arrows.add(new Arrow(from, to, port(outcome)));
            return this;
        }

        List<Card> list() { return List.copyOf(cards.values()); }
    }

    private static String port(int i) {
        return new Arrow("", "", "o" + i).outcome();
    }

    private static Flow chain(int length) {
        Flow flow = new Flow();
        for (int i = 0; i < length; i++) flow.card("C" + i, 1);
        for (int i = 1; i < length; i++) flow.wire("C" + (i - 1), 0, "C" + i);
        return flow;
    }

    /** The cards where {@code layout} put them. */
    private static List<Card> placed(Flow flow, Layout layout) {
        List<Card> placed = new ArrayList<>();
        for (Card card : flow.cards.values()) placed.add(card.at(layout.at().get(card.name())));
        return placed;
    }

    /** Every readability check, on {@code flow} as Auto-arrange lays it out. */
    private static Layout readable(Flow flow) {
        Layout layout = FlowArrange.arrange(flow.list(), flow.arrows, "");
        List<Card> placed = placed(flow, layout);
        List<Route> routes = FlowArrange.routes(placed, flow.arrows);

        cardsKeepTheirDistance(placed);
        everyCardIsOnTheCanvas(placed, layout);
        noWireCrossesACard(placed, routes);
        assertEquals(layout, FlowArrange.arrange(placed, flow.arrows, ""), "arranging twice moves nothing");
        assertTrue(layout.crossings() <= FlowArrange.arrange(flow.list(), flow.arrows, "", 1).crossings(),
                "the sweeps never end with more crossings than one pass");
        Box box = FlowArrange.extent(placed, routes);
        assertTrue(box.minX() >= 0 && box.minY() >= 0 && box.maxX() <= layout.width()
                && box.maxY() <= layout.height(), "every wire is on the canvas: " + box);
        return layout;
    }

    private static void cardsKeepTheirDistance(List<Card> cards) {
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                Card a = cards.get(i);
                Card b = cards.get(j);
                double apartX = Math.max(b.x() - a.right(), a.x() - b.right());
                double apartY = Math.max(b.y() - a.bottom(), a.y() - b.bottom());
                assertTrue(Math.max(apartX, apartY) >= FlowArrange.ROW_GAP - 1e-6,
                        a.name() + " and " + b.name() + " are " + Math.max(apartX, apartY) + "px apart");
            }
        }
    }

    private static void everyCardIsOnTheCanvas(List<Card> cards, Layout layout) {
        for (Card card : cards) {
            assertTrue(card.x() >= 0 && card.y() >= 0 && card.right() <= layout.width()
                    && card.bottom() <= layout.height(), card.name() + " is off the canvas");
        }
    }

    /** No point of a wire, away from its two ports, lies inside a card. */
    private static void noWireCrossesACard(List<Card> cards, List<Route> routes) {
        for (Route route : routes) {
            Routes.Route path = route.path();
            for (Pt p : path.sample(3)) {
                if (near(p, path.start()) || near(p, path.end())) continue;
                for (Card card : cards) {
                    if (card.rect().holds(p, 0.5)) {
                        fail(route.arrow() + " runs through " + card.name() + " at " + p);
                    }
                }
            }
        }
    }

    private static boolean near(Pt a, Pt b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y()) <= 2 * FlowArrange.PORT_INSET;
    }

    @Test
    void aChainRunsLeftToRightOnOneLine() {
        Layout layout = readable(chain(5));
        for (int i = 1; i < 5; i++) {
            assertTrue(layout.at().get("C" + i).x() > layout.at().get("C" + (i - 1)).x());
        }
        assertEquals(0, layout.crossings());
    }

    @Test
    void aThreeWayBranchFansOutInPortOrder() {
        Flow flow = new Flow().card("A", 3).card("B", 1).card("C", 1).card("D", 1).card("E", 1)
                .wire("A", 2, "D").wire("A", 0, "B").wire("A", 1, "C")
                .wire("B", 0, "E").wire("C", 0, "E").wire("D", 0, "E");
        Layout layout = readable(flow);
        assertTrue(layout.at().get("B").y() < layout.at().get("C").y()
                && layout.at().get("C").y() < layout.at().get("D").y(), "the cards follow the ports: " + layout);
        assertEquals(0, layout.crossings());
    }

    @Test
    void aLoopBackGoesAroundTheCards() {
        readable(new Flow().card("A", 1).card("B", 1).card("C", 2)
                .wire("A", 0, "B").wire("B", 0, "C").wire("C", 1, "A").wire("C", 0, "B"));
    }

    @Test
    void aSelfLoopGoesAroundItsCard() {
        readable(new Flow().card("A", 2).card("B", 1).card("Z", 1)
                .wire("A", 1, "A").wire("A", 0, "B").wire("Z", 0, "Z"));
    }

    @Test
    void aWireSkippingThreeColumnsPassesBetweenTheirCards() {
        Flow flow = new Flow().card("A", 2);
        for (String name : List.of("B", "C", "D")) flow.card(name, 2).card(name + "2", 1);
        flow.card("E", 1).wire("A", 0, "B").wire("B", 0, "C").wire("C", 0, "D").wire("D", 0, "E")
                .wire("A", 1, "E").wire("B", 1, "C2").wire("C", 1, "D2").wire("D", 1, "B2")
                .wire("B2", 0, "E");
        readable(flow);
    }

    @Test
    void fifteenColumnsGrowTheCanvas() {
        Layout layout = readable(chain(15));
        assertTrue(layout.width() > FlowArrange.MIN_W, "15 columns need more than the old 3000px");
    }

    @Test
    void fortyCardsWithLoopsStayReadable() {
        Random random = new Random(42);
        Flow flow = new Flow();
        int cards = 40;
        for (int i = 0; i < cards; i++) flow.card("N" + i, 1 + random.nextInt(3));
        for (int i = 0; i < cards - 1; i++) {
            int outcomes = flow.cards.get("N" + i).ports().size();
            flow.wire("N" + i, 0, "N" + Math.min(cards - 1, i + 1 + random.nextInt(3)));
            if (outcomes > 1 && random.nextInt(4) == 0) flow.wire("N" + i, 1, "N" + random.nextInt(i + 1));
            else if (outcomes > 1) flow.wire("N" + i, 1, "N" + Math.min(cards - 1, i + 2 + random.nextInt(4)));
        }
        readable(flow);
    }

    @Test
    void orphansGoBelowTheFlow() {
        Flow flow = chain(3).card("Lost", 1).card("Alone", 2).wire("Lost", 0, "C1").wire("Alone", 1, "Alone");
        Layout layout = readable(flow);
        double flowBottom = 0;
        for (int i = 0; i < 3; i++) flowBottom = Math.max(flowBottom, layout.at().get("C" + i).y());
        assertTrue(layout.at().get("Lost").y() > flowBottom && layout.at().get("Alone").y() > flowBottom);
    }

    @Test
    void cardsWithNoWireAreGridded() {
        Flow flow = new Flow();
        for (int i = 0; i < 7; i++) flow.card("G" + i, 1 + i % 3);
        readable(flow);
    }

    @Test
    void aWireStaysClearOfACardDraggedIntoItsWay() {
        Flow flow = chain(2);
        Layout layout = FlowArrange.arrange(flow.list(), flow.arrows, "");
        List<Card> placed = new ArrayList<>(placed(flow, layout));
        Pt a = layout.at().get("C0");
        Pt b = layout.at().get("C1");
        // Pull C1 far right and drop a stranger halfway, level with the wire.
        placed.set(1, placed.get(1).at(new Pt(b.x() + 900, b.y())));
        Card stranger = new Card("Stranger", (a.x() + b.x() + 900) / 2, a.y() - 20, 220, 120, Map.of());
        placed.add(stranger);
        List<Route> routes = FlowArrange.routes(placed, flow.arrows);
        Routes.Route first = routes.getFirst().path();
        Routes.Route straight = new Routes.Route(first.link(), List.of(new Routes.Line(first.start(), first.end())));
        assertTrue(straight.sample(3).stream().anyMatch(p -> stranger.rect().holds(p, 0.5)),
                "the straight wire would run through the stranger");
        noWireCrossesACard(placed, routes);
    }

    @Test
    void recenterFitsEverythingIntoTheView() {
        Flow flow = chain(15);
        Layout layout = FlowArrange.arrange(flow.list(), flow.arrows, "");
        List<Card> placed = placed(flow, layout);
        Box box = FlowArrange.extent(placed, FlowArrange.routes(placed, flow.arrows));
        double zoom = FlowArrange.fitZoom(box, 1200, 800);
        assertTrue(zoom < 1, "15 columns do not fit 1200px at 1:1");
        assertTrue((box.width() + 2 * FlowArrange.FIT_MARGIN) * zoom <= 1200 + 1e-6);
        assertTrue((box.height() + 2 * FlowArrange.FIT_MARGIN) * zoom <= 800 + 1e-6);
        assertEquals(1, FlowArrange.fitZoom(new Box(0, 0, 300, 200), 1200, 800), "a small flow stays at 1:1");
    }

    /** A collect-battle-rest flow: Collect loops on itself, Battle and Rest loop back to it. */
    @Test
    void aCollectBattleRestFlowRunsLeftToRightWithEveryWireKept() {
        Flow flow = new Flow().card("Collect", 2).card("Battle", 2).card("Rest", 1)
                .wire("Collect", 0, "Collect").wire("Collect", 1, "Battle")
                .wire("Battle", 0, "Collect").wire("Battle", 1, "Rest").wire("Rest", 0, "Collect");
        Layout layout = FlowArrange.arrange(flow.list(), flow.arrows, "Collect");
        readable(flow);
        assertTrue(layout.at().get("Collect").x() < layout.at().get("Battle").x()
                && layout.at().get("Battle").x() < layout.at().get("Rest").x(), "left to right: " + layout);
        List<Route> routes = FlowArrange.routes(placed(flow, layout), flow.arrows);
        assertEquals(flow.arrows, routes.stream().map(Route::arrow).toList(), "each wire keeps its outcome");
    }
}
