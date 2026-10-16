package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Purpose;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants.InputName;
import com.botmaker.sdk.internal.screens.MapScreen;
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
 * each edge, and each input's row in the side panel, by its field. Values, so a redraw happens only when one of them
 * changed.
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
     * @param chores    its inputs named a task
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
     * One edge: {@code via}, the field {@code field} of {@code from}, reaches {@code to}. The entrance (the screen's
     * {@code from}) is solid and places the cards; another way in and a way back are dashed.
     */
    record Link(String from, String to, String field, Input via, boolean entrance, boolean back, String when,
                String look) {

        /** How the link's input reads; a way back with none of its own reads as the default one. */
        String label() {
            if (field != null) return field;
            return via == null ? "the default way back (a close button, then Escape)" : via.label();
        }
    }

    /** One of a card's pills: its words and its style class ({@code screen-pill-untried}). */
    record Pill(String text, String style) {}

    /**
     * One input in the side panel.
     *
     * @param field   its field on the screen's class
     * @param input   the input as the map holds it
     * @param label   how it reads: its field, {@code beerBuy}
     * @param picture its picture's id (a strip's first member's), null for a key
     * @param state   how it stands
     * @param result  what it did, in words: {@code → Campaign}, {@code in place}, {@code never: spends gems}
     * @param kind    what it is for, lower case; empty when not named
     * @param look    the screen's look only it is on ({@code Halloween}); empty for one on every look
     * @param when    the method saying when it is there, {@code Firestone::eventOn}; empty for none
     * @param looks   each look of its button, the usual one first, with where it leads in that look: one entry for
     *                a button with one look
     */
    record Row(String field, Input input, String label, String picture, RowState state, String result, String kind,
               String look, String when, List<ButtonLook> looks) {}

    /**
     * One look of an input's button as the side panel steps through it: its picture, what it means, and where it
     * leads while it looks so; {@code leads} empty when the ways do not tell the looks apart.
     */
    record ButtonLook(String picture, com.botmaker.sdk.api.screens.State state, String leads) {}

    /**
     * One strip of a screen as its shot draws it: every member's picture in the strip's order, over the place the
     * members were cut in, scrolled by the wheel; a member that comes and goes is drawn dimmed.
     *
     * @param place   the members' places joined, in the game's frame
     * @param down    whether the strip runs down (a column), not across
     * @param members each member's row, in the strip's order
     */
    record Strip(Rect place, boolean down, List<Row> members) {

        /** Whether {@code row} is drawn in this strip, so the shot boxes it nowhere else. */
        boolean has(Row row) {
            return members.stream().anyMatch(m -> m.picture() != null && m.picture().equals(row.picture()));
        }
    }

    private ScreenCards() {}

    /**
     * {@code constant}'s strips as its shot draws them, from {@code rows} ({@link #rows}); a strip none of whose
     * members has a place it was cut at is not drawn.
     */
    static List<Strip> strips(ScreenGraph graph, String constant, List<Row> rows) {
        MapScreen screen = graph.screens().get(constant);
        if (screen == null) return List.of();
        List<Strip> strips = new ArrayList<>();
        for (com.botmaker.sdk.api.screens.Strip strip : screen.strips().values()) {
            Rect place = null;
            List<Row> members = new ArrayList<>();
            for (ImageTemplate member : strip.members()) {
                rows.stream().filter(r -> r.input() instanceof Button && member.id().equals(r.picture())).findFirst()
                        .ifPresent(members::add);
                Rect at = member.place();
                if (at != null) place = place == null ? at : union(place, at);
            }
            if (place != null && !members.isEmpty()) {
                strips.add(new Strip(place, place.height() >= place.width(), List.copyOf(members)));
            }
        }
        return strips;
    }

    private static Rect union(Rect a, Rect b) {
        int left = Math.min(a.x(), b.x());
        int top = Math.min(a.y(), b.y());
        return new Rect(left, top, Math.max(a.x() + a.width(), b.x() + b.width()) - left,
                Math.max(a.y() + a.height(), b.y() + b.height()) - top);
    }

    /** Every screen's card, in the order the bot declares them; {@code stages} by constant. */
    static List<Card> cards(ScreenGraph graph, Map<String, String> stages) {
        List<Card> cards = new ArrayList<>();
        for (String constant : graph.screens().keySet()) cards.add(card(graph, constant, stages.getOrDefault(constant, "")));
        return cards;
    }

    static Card card(ScreenGraph graph, String constant, String stage) {
        MapScreen screen = graph.screens().get(constant);
        List<String> fields = graph.fields(constant);
        int tried = (int) fields.stream().filter(f -> graph.tried(constant, f)).count();
        int chores = (int) fields.stream()
                .filter(f -> graph.input(constant, f).filter(i -> i.purpose() == Purpose.TASK).isPresent()).count();
        int untried = graph.untried(constant).size();
        int unseen = graph.unseen(constant).size();
        State state;
        String said;
        if (!screen.hasMarker()) {
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
        return new Card(constant, graph.shown(constant), screen.hasMarker() ? screen.firstMarker().id() : null, state,
                said, shape, screen.isPopup(), graph.presence(constant), tried, fields.size(), chores,
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
            MapScreen to = graph.screens().get(edge.to());
            Optional<InputName> tree = graph.nameOf(to.fromInput());
            boolean entrance = !edge.back() && edge.input() != null
                    && tree.filter(n -> n.screen().equals(edge.from()) && n.field().equals(edge.input())).isPresent()
                    && entered.add(edge.to());
            links.add(new Link(edge.from(), edge.to(), edge.input(), edge.via(), entrance, edge.back(),
                    edge.when() == null ? "" : ScreenTypes.sourceOf(edge.when()),
                    edge.state() == null ? "" : edge.state().displayName().toLowerCase(Locale.ROOT)));
        }
        return links;
    }

    /** The screen the map's tree starts at: the first that is no popup and is made known from none. */
    static Optional<String> root(ScreenGraph graph) {
        return graph.root();
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

    /** {@code constant}'s inputs as the side panel lists them, in the order its class declares them. */
    static List<Row> rows(ScreenGraph graph, String constant) {
        List<Row> rows = new ArrayList<>();
        for (String field : graph.fields(constant)) rows.add(row(graph, constant, field));
        return rows;
    }

    static Row row(ScreenGraph graph, String constant, String field) {
        Input input = graph.input(constant, field).orElseThrow();
        Optional<String> opens = graph.opens(constant, field);
        RowState state;
        String result;
        if (input.never() != null) {
            state = RowState.NEVER;
            result = "never: " + input.never();
        } else if (input.lock() != null) {
            state = RowState.LOCKED;
            result = "locked";
        } else if (graph.tried(constant, field)) {
            // As the graph counts a tried input, so the card's ring and the rows never disagree.
            state = RowState.TRIED;
            result = opens.isPresent() ? "→ " + String.join(" / ", graph.leads(constant, field).stream()
                    .map(lead -> graph.shown(lead.to()) + said(lead.state(), lead.when())).distinct().toList())
                    : graph.isBack(constant, field) ? "way back"
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
        String kind = input.purpose() != null ? input.purpose().name().toLowerCase(Locale.ROOT)
                : input.comesAndGoes() ? "comes and goes" : "";
        MapScreen screen = graph.screens().get(constant);
        InputName self = new InputName(constant, field);
        String look = screen == null ? "" : screen.variants().stream()
                .filter(v -> v.inputs().stream().anyMatch(i -> graph.nameOf(i).filter(self::equals).isPresent()))
                .map(ScreenVariant::name).findFirst().orElse("");
        List<ScreenGraph.Lead> leads = graph.leads(constant, field);
        List<ButtonLook> looks = input instanceof Button button ? button.allLooks().stream()
                .map(v -> new ButtonLook(v.picture().id(), v.state(), String.join(" / ", leads.stream()
                        .filter(l -> l.state() == null || l.state() == v.state())
                        .map(l -> graph.shown(l.to()) + said(null, l.when())).distinct().toList()))).toList()
                : List.of();
        return new Row(field, input, field, picture(input), state, result, kind, look,
                ScreenTypes.sourceOf(input.when()), looks);
    }

    /** The picture {@code input} acts on: a button's, a strip's first member's; null for a key. */
    static String picture(Input input) {
        return switch (input) {
            case Button button -> button.picture() == null ? null : button.picture().id();
            case com.botmaker.sdk.api.screens.Strip strip -> strip.members().isEmpty() ? null
                    : strip.members().getFirst().id();
            case Hotkey ignored -> null;
        };
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
