package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What 🗺 Screens draws of the map, decided apart from JavaFX: each screen's card (its state, progress and badges),
 * each edge, and each input's row in the side panel. Values, so a redraw happens only when one of them changed.
 */
final class ScreenCards {

    /** How far Discovery got with a screen, the card's colour. */
    enum State {
        /** It owes its marker, or has nothing cut: red. */
        OWES,
        /** It has inputs left to try: amber. */
        UNTRIED,
        /** Every input is tried, never done or locked: green. */
        FINISHED
    }

    /** How an input stands, its row's colour. */
    enum RowState { TRIED, UNTRIED, UNSEEN, NEVER, LOCKED }

    /**
     * One screen's card.
     *
     * @param constant  its constant in {@code Screens}
     * @param name      the name the game shows
     * @param marker    its marker's picture, null until cut
     * @param state     how far Discovery got
     * @param said      the state in words: {@code 3 untried}, {@code owes its marker}
     * @param shape     {@code menu} or {@code activity} once finished, else empty
     * @param popup     whether it opens on its own
     * @param presence  whether it can be reached now
     * @param tried     its inputs tried
     * @param inputs    its inputs
     * @param chores    its inputs named a chore
     * @param stage     what its notes say it reached ({@code surveyed}), empty when nothing
     */
    record Card(String constant, String name, String marker, State state, String said, String shape, boolean popup,
                ScreenGraph.Presence presence, int tried, int inputs, int chores, String stage) {

        /** Whether it shows only at times, or is locked: drawn dashed. */
        boolean conditional() {
            return presence != ScreenGraph.Presence.ALWAYS;
        }
    }

    /**
     * One edge: {@code via} on {@code from} reaches {@code to}. The entrance (the screen's {@code from}) is solid and
     * places the cards; another way in and a way back are dashed.
     */
    record Link(String from, String to, Input via, boolean entrance, boolean back) {}

    /**
     * One input in the side panel.
     *
     * @param input   the input as the map holds it
     * @param label   how it reads: {@code click campaign_claim}
     * @param picture its picture's id, null for a key
     * @param state   how it stands
     * @param result  what it did, in words: {@code → Campaign}, {@code in place}, {@code never: spends gems}
     * @param kind    what it is for, lower case; empty when not named
     */
    record Row(Input input, String label, String picture, RowState state, String result, String kind) {}

    private ScreenCards() {}

    /** Every screen's card, in the order the bot declares them; {@code stages} by constant. */
    static List<Card> cards(ScreenGraph graph, Map<String, String> stages) {
        List<Card> cards = new ArrayList<>();
        for (String constant : graph.screens().keySet()) cards.add(card(graph, constant, stages.getOrDefault(constant, "")));
        return cards;
    }

    static Card card(ScreenGraph graph, String constant, String stage) {
        GameScreen screen = graph.screens().get(constant);
        List<Input> inputs = screen.inputs();
        int tried = (int) inputs.stream().filter(i -> graph.tried(constant, i)).count();
        int chores = (int) inputs.stream().filter(i -> i.kind() == Kind.CHORE).count();
        int untried = graph.untried(constant).size();
        int unseen = graph.unseen(constant).size();
        boolean noneCut = inputs.isEmpty() && screen.back() == null && !screen.isPopup();
        State state;
        String said;
        if (screen.marker() == null) {
            state = State.OWES;
            said = "owes its marker";
        } else if (noneCut) {
            state = State.OWES;
            said = "nothing cut";
        } else if (untried > 0) {
            state = State.UNTRIED;
            said = untried + " untried";
        } else {
            state = State.FINISHED;
            said = unseen > 0 ? "finished, " + unseen + " not seen" : "finished";
        }
        String shape = state == State.FINISHED
                ? graph.shape(constant).map(s -> s.name().toLowerCase(Locale.ROOT)).orElse("") : "";
        return new Card(constant, graph.shown(constant), screen.marker() == null ? null : screen.marker().id(), state,
                said, shape, screen.isPopup(), graph.presence(constant), tried, inputs.size(), chores,
                stage == null ? "" : stage);
    }

    /** Every edge, each entrance marked: the one the screen it reaches was made known by. */
    static List<Link> links(ScreenGraph graph) {
        List<Link> links = new ArrayList<>();
        Set<String> entered = new HashSet<>();
        for (ScreenGraph.Edge edge : graph.edges()) {
            GameScreen to = graph.screens().get(edge.to());
            boolean entrance = !edge.back() && to.from() != null && edge.via() != null
                    && edge.via().sameAs(to.from().via())
                    && graph.parent(edge.to()).filter(edge.from()::equals).isPresent()
                    && entered.add(edge.to());
            links.add(new Link(edge.from(), edge.to(), edge.via(), entrance, edge.back()));
        }
        return links;
    }

    /** The screen the map's tree starts at: the first that is no popup and is made known from none. */
    static Optional<String> root(ScreenGraph graph) {
        return graph.screens().entrySet().stream().filter(e -> !e.getValue().isPopup() && e.getValue().from() == null)
                .map(Map.Entry::getKey).findFirst();
    }

    /** The names on the way from the first screen to {@code constant}, both included; just it for a popup. */
    static List<String> path(ScreenGraph graph, String constant) {
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String at = constant; at != null && seen.add(at); at = graph.parent(at).orElse(null)) {
            names.addFirst(graph.shown(at));
        }
        return names;
    }

    /** {@code constant}'s inputs as the side panel lists them, in the map's order. */
    static List<Row> rows(ScreenGraph graph, String constant) {
        GameScreen screen = graph.screens().get(constant);
        if (screen == null) return List.of();
        List<Row> rows = new ArrayList<>();
        for (Input input : screen.inputs()) rows.add(row(graph, constant, input));
        return rows;
    }

    static Row row(ScreenGraph graph, String constant, Input input) {
        Optional<String> opens = graph.opens(constant, input);
        RowState state;
        String result;
        if (input.never() != null) {
            state = RowState.NEVER;
            result = "never: " + input.never();
        } else if (input.lock() != null) {
            state = RowState.LOCKED;
            result = "locked";
        } else if (opens.isPresent()) {
            state = RowState.TRIED;
            result = "→ " + graph.shown(opens.get());
        } else if (input.did() != null) {
            state = RowState.TRIED;
            result = switch (input.did()) {
                case IN_PLACE -> "in place";
                case NONE -> "no effect";
                case LEAVES -> "leaves";
            };
        } else if (input.comesAndGoes()) {
            state = RowState.UNSEEN;
            result = "not seen yet";
        } else {
            state = RowState.UNTRIED;
            result = "untried";
        }
        String kind = input.kind() != null ? input.kind().name().toLowerCase(Locale.ROOT)
                : input.comesAndGoes() ? "comes and goes" : "";
        return new Row(input, input.label(), input.picture() == null ? null : input.picture().id(), state, result,
                kind);
    }
}
