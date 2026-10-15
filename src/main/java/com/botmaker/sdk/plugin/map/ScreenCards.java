package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

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
     * @param at        where its card was moved to, null for where the layout puts it
     */
    record Card(String constant, String name, String marker, State state, String said, String shape, boolean popup,
                ScreenGraph.Presence presence, int tried, int inputs, int chores, String stage, Point at) {

        /** Whether it shows only at times, or is locked: drawn dashed. */
        boolean conditional() {
            return presence != ScreenGraph.Presence.ALWAYS;
        }
    }

    /**
     * One edge: {@code via} on {@code from} reaches {@code to}. The entrance (the screen's {@code from}) is solid and
     * places the cards; another way in and a way back are dashed.
     */
    record Link(String from, String to, Input via, boolean entrance, boolean back, String when, String look) {

        /** How the link's input reads; a way back with none of its own reads as the default one. */
        String label() {
            return via == null ? "the default way back (a close button, then Escape)" : via.label();
        }
    }

    /** One of a card's pills: its words and its style class ({@code screen-pill-untried}). */
    record Pill(String text, String style) {}

    /**
     * One input in the side panel.
     *
     * @param input   the input as the map holds it
     * @param label   how it reads: {@code click campaign_claim}
     * @param picture its picture's id, null for a key
     * @param state   how it stands
     * @param result  what it did, in words: {@code → Campaign}, {@code in place}, {@code never: spends gems}
     * @param kind    what it is for, lower case; empty when not named
     * @param look    the screen's look only it is on ({@code Halloween}); empty for one on every look
     * @param when    the method saying when it is there, {@code Firestone::eventOn}; empty for none
     * @param looks   each look of its button, the usual one first, with where it leads in that look: one entry for
     *                a button with one look
     */
    record Row(Input input, String label, String picture, RowState state, String result, String kind, String look,
               String when, List<ButtonLook> looks) {}

    /**
     * One look of an input's button as the side panel steps through it: its picture, what it means, and where it
     * leads while it looks so; {@code leads} empty when the ways do not tell the looks apart.
     */
    record ButtonLook(String picture, com.botmaker.sdk.api.screens.State state, String leads) {}

    private ScreenCards() {}

    /** Every screen's card, in the order the bot declares them; {@code stages} by constant. */
    static List<Card> cards(ScreenGraph graph, Map<String, String> stages) {
        List<Card> cards = new ArrayList<>();
        for (String constant : graph.screens().keySet()) cards.add(card(graph, constant, stages.getOrDefault(constant, "")));
        return cards;
    }

    static Card card(ScreenGraph graph, String constant, String stage) {
        GameScreen screen = graph.screens().get(constant);
        List<Input> inputs = screen.allInputs();
        int tried = (int) inputs.stream().filter(i -> graph.tried(constant, i)).count();
        int chores = (int) inputs.stream().filter(i -> i.kind() == Kind.CHORE).count();
        int untried = graph.untried(constant).size();
        int unseen = graph.unseen(constant).size();
        State state;
        String said;
        if (screen.marker() == null) {
            state = State.OWES;
            said = "owes its marker";
        } else if (graph.noneCut(constant)) {
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
                stage == null ? "" : stage, screen.at());
    }

    /**
     * {@code card}'s pills, as its card, the side panel, the constant view and read_map all say them: how far it got,
     * its shape, popup, whether it is reached only at times or is locked, and the stage its notes say it reached.
     */
    static List<Pill> pills(Card card) {
        List<Pill> pills = new ArrayList<>();
        pills.add(new Pill(card.said(), "screen-pill-" + card.state().name().toLowerCase(Locale.ROOT)));
        if (!card.shape().isEmpty()) pills.add(new Pill(card.shape(), "screen-pill-plain"));
        if (card.popup()) pills.add(new Pill("popup", "screen-pill-plain"));
        if (card.conditional()) pills.add(new Pill(card.presence().name().toLowerCase(Locale.ROOT), "screen-pill-plain"));
        if (!card.stage().isEmpty()) pills.add(new Pill(card.stage(), "screen-pill-stage"));
        return pills;
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
            links.add(new Link(edge.from(), edge.to(), edge.via(), entrance, edge.back(),
                    edge.when() == null ? "" : ScreenTypes.sourceOf(edge.when()),
                    edge.state() == null ? "" : edge.state().displayName().toLowerCase(Locale.ROOT)));
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
        for (Input input : screen.allInputs()) rows.add(row(graph, constant, input));
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
        } else if (graph.tried(constant, input)) {
            // As the graph counts a tried input, so the card's ring and the rows never disagree.
            state = RowState.TRIED;
            result = opens.isPresent() ? "→ " + String.join(" / ", graph.leads(constant, input).stream()
                    .map(lead -> graph.shown(lead.to()) + said(lead.state(), lead.when())).distinct().toList())
                    : input.did() == null ? "tried" : switch (input.did()) {
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
        GameScreen screen = graph.screens().get(constant);
        String look = screen == null ? "" : screen.variants().stream().filter(v -> v.inputs().contains(input))
                .map(ScreenVariant::name).findFirst().orElse("");
        List<ScreenGraph.Lead> leads = graph.leads(constant, input);
        List<ButtonLook> looks = input.looks().stream().map(v -> new ButtonLook(v.picture().id(), v.state(),
                String.join(" / ", leads.stream().filter(l -> l.state() == null || l.state() == v.state())
                        .map(l -> graph.shown(l.to()) + said(null, l.when())).distinct().toList()))).toList();
        return new Row(input, input.label(), input.picture() == null ? null : input.picture().id(), state, result,
                kind, look, ScreenTypes.sourceOf(input.when()), looks);
    }

    /** What a way needs, in words after the screen it leads to: {@code  (disabled, while Firestone::eventOn)}. */
    private static String said(com.botmaker.sdk.api.screens.State state, BooleanSupplier when) {
        List<String> needs = new ArrayList<>();
        if (state != null) needs.add(state.displayName().toLowerCase(Locale.ROOT));
        String method = ScreenTypes.sourceOf(when);
        if (!method.isEmpty()) needs.add("while " + method);
        return needs.isEmpty() ? "" : " (" + String.join(", ", needs) + ")";
    }
}
