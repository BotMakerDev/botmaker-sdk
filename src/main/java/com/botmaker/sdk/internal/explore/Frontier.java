// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * The presses a goal-directed run has not made yet, best first (spec 2026-11-24-explore-agents-design § Goal-directed
 * exploration): each a press of a screen the Reader ranked by how likely it leads to the goal and tagged by how
 * dangerous it is. Never pruned: an off-goal press stays at its low rank and comes once the better ones are gone. A
 * press leaves only by being pressed or parked: past the depth cap (taken back by {@link #resume}) or needing the user,
 * a press nobody watching may make ({@link PressTag#unattended}).
 *
 * <p>Rebuilt from the journal ({@link #of}): {@link ExploreEvent.Ranked} adds, {@link ExploreEvent.Tried} removes, so
 * a later run that carries the ranks over resumes it.
 */
final class Frontier {

    /** {@code press} of {@code screen}, its {@code rank} (higher first) and {@code tag}. */
    record Entry(String screen, String press, int rank, PressTag tag) {
        String key() {
            return keyOf(screen, press);
        }
    }

    /** Why an entry is parked. */
    enum Hold {
        /** Its screen is deeper than the cap: a later run, or a raised cap, takes it back. */
        DEPTH,
        /** Nobody watching may press it: it waits on the user. */
        NEEDS_YOU
    }

    /** A parked entry, {@code why} in plain words. */
    record Parked(Entry entry, Hold hold, String why) {}

    /**
     * Labels a press is never made unattended by, whatever its tag: the Reader's tag can miss one (spec § Unattended
     * safety). Whole words, any case.
     */
    private static final Pattern DENIED = Pattern.compile(
            "\\b(sign ?out|log ?out|delete|erase|reset|wipe|uninstall|remove account|buy|purchase|pay|subscribe|"
                    + "checkout|order now)\\b");

    /** The number of edges from the run's home to a screen: {@link Integer#MAX_VALUE} for one no way reaches. */
    private final ToIntFunction<String> depthOf;
    private final Map<String, Entry> open = new LinkedHashMap<>();
    private final Map<String, Parked> parked = new LinkedHashMap<>();

    Frontier(ToIntFunction<String> depthOf) {
        this.depthOf = depthOf;
    }

    /** The frontier {@code events} leave: each ranked press not tried since, a newer rank replacing an older one. */
    static Frontier of(List<ExploreEvent> events, ToIntFunction<String> depthOf) {
        Frontier frontier = new Frontier(depthOf);
        for (ExploreEvent event : events) frontier.take(event);
        return frontier;
    }

    /** Takes {@code event} in: a rank adds its press, a try removes it; any other event changes nothing. */
    void take(ExploreEvent event) {
        switch (event) {
            case ExploreEvent.Ranked ranked -> add(ranked.screen(), ranked.press(), ranked.rank(), ranked.tag());
            case ExploreEvent.Tried tried -> pressed(tried.screen(), tried.by());
            default -> {}
        }
    }

    /** {@code screen.press} as one key. */
    static String keyOf(String screen, String press) {
        return screen + "." + press;
    }

    /**
     * Adds {@code press} of {@code screen} at {@code rank}, or ranks it again. A press whose label is denied is
     * {@link PressTag#DESTRUCTIVE} whatever {@code tag} says; one with no tag is {@link PressTag#SAFE} unless denied.
     * One nobody watching may press is parked {@link Hold#NEEDS_YOU} at once.
     */
    Entry add(String screen, String press, int rank, PressTag tag) {
        Entry entry = new Entry(screen, press, rank, tagOf(press, tag));
        String key = entry.key();
        open.remove(key);
        Parked was = parked.remove(key);
        if (!entry.tag().unattended()) {
            parked.put(key, new Parked(entry, Hold.NEEDS_YOU, entry.tag().displayName() + ": pressed only by you"));
        } else if (was != null && was.hold() == Hold.DEPTH) {
            // A new rank does not take back what the depth cap parked: resume() does.
            parked.put(key, new Parked(entry, Hold.DEPTH, was.why()));
        } else {
            open.put(key, entry);
        }
        return entry;
    }

    /** {@code tag}, or the denylist's: a denied label is destructive, an untagged one safe. */
    static PressTag tagOf(String press, PressTag tag) {
        if (denied(press)) return PressTag.DESTRUCTIVE;
        return tag == null ? PressTag.SAFE : tag;
    }

    /** Whether {@code label} names a press nobody watching makes: sign out, delete, reset, buy, … */
    static boolean denied(String label) {
        if (label == null) return false;
        // An input's field is camelCase (deleteAccount): its words are matched, as a label's are.
        String words = label.replaceAll("(\\p{Ll})(\\p{Lu})", "$1 $2").replace('_', ' ').replace('-', ' ');
        return DENIED.matcher(words.toLowerCase(Locale.ROOT)).find();
    }

    /** {@code press} of {@code screen} was made: it leaves the frontier, open or parked. */
    void pressed(String screen, String press) {
        String key = keyOf(screen, press);
        open.remove(key);
        parked.remove(key);
    }

    /**
     * The best open press within {@code depthCap} edges of home, the first ranked among equals. Each better one past
     * the cap is parked {@link Hold#DEPTH} on the way. Empty when none is left within the cap.
     */
    Optional<Entry> next(int depthCap) {
        List<Entry> best = new ArrayList<>(open.values());
        best.sort(Comparator.comparingInt(Entry::rank).reversed());
        for (Entry entry : best) {
            int depth = depthOf.applyAsInt(entry.screen());
            if (depth <= depthCap) return Optional.of(entry);
            park(entry, Hold.DEPTH, depth == Integer.MAX_VALUE ? "no way reaches " + entry.screen()
                    : entry.screen() + " is " + depth + " presses deep, past the cap of " + depthCap);
        }
        return Optional.empty();
    }

    /** Parks {@code entry} for {@code why}, resumable: {@link #resume} takes it back. */
    void park(Entry entry, String why) {
        park(entry, Hold.DEPTH, why);
    }

    private void park(Entry entry, Hold hold, String why) {
        open.remove(entry.key());
        parked.put(entry.key(), new Parked(entry, hold, why));
    }

    /** Takes back every entry parked for its depth or its way, never one that needs the user: what came back. */
    List<Entry> resume() {
        List<Entry> back = new ArrayList<>();
        parked.values().removeIf(p -> {
            if (p.hold() == Hold.NEEDS_YOU) return false;
            back.add(p.entry());
            open.put(p.entry().key(), p.entry());
            return true;
        });
        return back;
    }

    /** Whether nothing is open: every press made or parked. */
    boolean isEmpty() {
        return open.isEmpty();
    }

    /** Whether the frontier ever held anything: a run nobody ranked for presses as before. */
    boolean ranked() {
        return !open.isEmpty() || !parked.isEmpty();
    }

    /** The presses waiting on the user, in the order they were parked. */
    List<Parked> needsYou() {
        return parked.values().stream().filter(p -> p.hold() == Hold.NEEDS_YOU).toList();
    }

    /** Whether {@code press} of {@code screen} waits on the user: the run never makes it, whatever was retried. */
    boolean held(String screen, String press) {
        Parked p = parked.get(keyOf(screen, press));
        return p != null && p.hold() == Hold.NEEDS_YOU;
    }

    /** Every parked press, in the order it was parked. */
    List<Parked> parked() {
        return List.copyOf(parked.values());
    }

    /** What a later run carries over: each press not made, open or parked, as the rank that adds it again. */
    List<ExploreEvent.Ranked> saved(long at) {
        List<ExploreEvent.Ranked> saved = new ArrayList<>();
        for (Entry entry : open.values()) saved.add(ranked(entry, at));
        for (Parked p : parked.values()) saved.add(ranked(p.entry(), at));
        return saved;
    }

    private static ExploreEvent.Ranked ranked(Entry entry, long at) {
        return new ExploreEvent.Ranked(entry.screen(), entry.press(), entry.rank(), entry.tag(), at);
    }
}
