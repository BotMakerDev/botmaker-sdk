package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.map.ScreenCards.RowState;
import com.botmaker.sdk.plugin.map.ScreenCards.State;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 🗺 Screens' outline decided apart from JavaFX: the map as a text tree, each screen with its inputs under it and the
 * screen an input is the entrance of under that input, from the first screen; popups and screens the tree does not
 * reach in a group each (the user's call, 2026-10-18). A filter keeps what matches and the way down to it.
 */
final class Outline {

    /** What an entry is. */
    enum Kind { GROUP, SCREEN, INPUT }

    /** A group's name. */
    static final String POPUPS = "Popups";
    static final String UNREACHED = "Not reached from the first screen";

    /**
     * One line of the outline.
     *
     * @param kind     what it is
     * @param label    a group's name; empty otherwise
     * @param constant the screen it is or is an input of; null for a group
     * @param card     a screen's card; null otherwise
     * @param row      an input's row; null otherwise
     * @param children what is under it: a screen's inputs, the screen an input enters, a group's screens
     */
    record Entry(Kind kind, String label, String constant, Card card, Row row, List<Entry> children) {

        static Entry group(String label, List<Entry> screens) {
            return new Entry(Kind.GROUP, label, null, null, null, List.copyOf(screens));
        }

        static Entry screen(Card card, List<Entry> inputs) {
            return new Entry(Kind.SCREEN, "", card.constant(), card, null, List.copyOf(inputs));
        }

        static Entry input(String constant, Row row, List<Entry> entered) {
            return new Entry(Kind.INPUT, "", constant, null, row, List.copyOf(entered));
        }

        Entry with(List<Entry> now) {
            return new Entry(kind, label, constant, card, row, List.copyOf(now));
        }

        /** What keeps it open or shut across redraws: its screen, and its input's field. */
        String key() {
            return switch (kind) {
                case GROUP -> "group " + label;
                case SCREEN -> constant;
                case INPUT -> constant + "." + row.field();
            };
        }
    }

    /**
     * What the outline shows: lines whose words hold {@code text} (any case), and with {@code owes} only screens owing
     * something, with {@code untried} only screens and inputs left to try.
     */
    record Filter(String text, boolean owes, boolean untried) {
        static final Filter NONE = new Filter("", false, false);

        Filter {
            text = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        }

        boolean isNone() {
            return text.isEmpty() && !owes && !untried;
        }

        boolean screen(Card card, List<String> terms) {
            if (owes && card.state() != State.OWES) return false;
            if (untried && card.state() != State.UNTRIED) return false;
            return holds(terms.stream());
        }

        boolean input(Row row) {
            if (owes) return false;
            if (untried && row.state() != RowState.UNTRIED && row.state() != RowState.UNSEEN) return false;
            return holds(Stream.of(row.field(), row.picture(), row.result(), row.kind()));
        }

        private boolean holds(Stream<String> words) {
            return text.isEmpty() || words.filter(Objects::nonNull)
                    .anyMatch(w -> w.toLowerCase(Locale.ROOT).contains(text));
        }
    }

    private Outline() {}

    /** The whole map's outline, from the cards and links 🗺 drew. */
    static List<Entry> entries(ScreenGraph graph, List<Card> cards, List<Link> links) {
        Map<String, Card> byConstant = new HashMap<>();
        for (Card card : cards) byConstant.put(card.constant(), card);
        Map<String, String> entered = new HashMap<>();
        for (Link link : links) {
            if (link.entrance() && link.field() != null) entered.put(link.from() + "." + link.field(), link.to());
        }
        Set<String> placed = new HashSet<>();
        List<Entry> top = new ArrayList<>();
        Optional<String> root = ScreenCards.root(graph).filter(byConstant::containsKey);
        root.ifPresent(r -> {
            placed.add(r);
            top.add(screen(graph, r, byConstant, entered, placed));
        });
        List<Entry> popups = new ArrayList<>();
        for (Card card : cards) {
            if (card.popup() && placed.add(card.constant())) {
                popups.add(screen(graph, card.constant(), byConstant, entered, placed));
            }
        }
        List<Entry> unreached = new ArrayList<>();
        for (Card card : cards) {
            if (placed.add(card.constant())) unreached.add(screen(graph, card.constant(), byConstant, entered, placed));
        }
        if (!popups.isEmpty()) top.add(Entry.group(POPUPS, popups));
        if (!unreached.isEmpty()) top.add(Entry.group(UNREACHED, unreached));
        return List.copyOf(top);
    }

    private static Entry screen(ScreenGraph graph, String constant, Map<String, Card> cards,
                                Map<String, String> entered, Set<String> placed) {
        List<Entry> inputs = new ArrayList<>();
        for (Row row : ScreenCards.rows(graph, constant)) {
            String to = entered.get(constant + "." + row.field());
            List<Entry> under = to != null && cards.containsKey(to) && placed.add(to)
                    ? List.of(screen(graph, to, cards, entered, placed)) : List.of();
            inputs.add(Entry.input(constant, row, under));
        }
        return Entry.screen(cards.get(constant), inputs);
    }

    /**
     * What of {@code entries} {@code filter} keeps: each line it matches and the lines down to it. A screen matched
     * keeps all its inputs, and the screens under them only where they match.
     */
    static List<Entry> filter(ScreenGraph graph, List<Entry> entries, Filter filter) {
        if (filter.isNone()) return entries;
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : entries) kept(graph, entry, filter).ifPresent(kept::add);
        return List.copyOf(kept);
    }

    private static Optional<Entry> kept(ScreenGraph graph, Entry entry, Filter filter) {
        return switch (entry.kind()) {
            case GROUP -> {
                List<Entry> screens = filter(graph, entry.children(), filter);
                yield screens.isEmpty() ? Optional.empty() : Optional.of(entry.with(screens));
            }
            case SCREEN -> {
                boolean hit = filter.screen(entry.card(), ScreenCards.terms(graph, entry.constant()));
                List<Entry> inputs = new ArrayList<>();
                for (Entry input : entry.children()) {
                    List<Entry> under = filter(graph, input.children(), filter);
                    if (hit || filter.input(input.row()) || !under.isEmpty()) inputs.add(input.with(under));
                }
                yield hit || !inputs.isEmpty() ? Optional.of(entry.with(inputs)) : Optional.empty();
            }
            case INPUT -> filter.input(entry.row()) ? Optional.of(entry) : Optional.empty();
        };
    }

    /** The keys of every entry, down the tree. */
    static Stream<Entry> all(List<Entry> entries) {
        return entries.stream().flatMap(e -> Stream.concat(Stream.of(e), all(e.children())));
    }
}
