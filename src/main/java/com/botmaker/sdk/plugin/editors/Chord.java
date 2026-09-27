package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * What the combination editor holds while it is open: keys in the order they were chosen, each once — which
 * is exactly what {@link Combo#of} writes, so a combo reads back as it was written. Any number of keys and
 * modifiers anywhere (feedback 2, 2026-09-27): it held modifiers plus one other key until then, and a combo of
 * two ordinary keys could not be picked at all. Pure: no JavaFX.
 */
record Chord(List<Key> keys) {

    static final List<Key> MODIFIER_ORDER = List.of(Key.CTRL, Key.ALT, Key.SHIFT, Key.META);
    static final Chord EMPTY = new Chord(List.of());

    Chord {
        keys = List.copyOf(new LinkedHashSet<>(keys));
    }

    /** One key alone — what the one-key editor holds. */
    static Chord single(Key key) {
        return new Chord(List.of(key));
    }

    /** A combo read back, in its own order; a key it repeats is held once. */
    static Chord of(Combo combo) {
        return new Chord(combo.keys());
    }

    /** A click on a cap or a chip: a key not in the combination joins it at the end; one in it leaves. */
    Chord click(Key key) {
        List<Key> next = new ArrayList<>(keys);
        if (!next.remove(key)) next.add(key);
        return new Chord(next);
    }

    /**
     * A real keystroke: the modifiers held for it that are not already in the combination, in the order a
     * person presses them, then the key. Appended, so a combination is recorded one keystroke at a time.
     */
    Chord press(boolean ctrl, boolean alt, boolean shift, boolean meta, Key key) {
        List<Key> next = new ArrayList<>(keys);
        boolean[] held = {ctrl, alt, shift, meta};
        for (int i = 0; i < held.length; i++) {
            if (held[i]) next.add(MODIFIER_ORDER.get(i));
        }
        next.add(key);
        return new Chord(next);
    }

    /**
     * The key at {@code from} put at {@code to} — a chip dragged to a new place, since the order is the press
     * order (feedback 3). A {@code from} that is not there moves nothing; a {@code to} past the end is the end.
     */
    Chord move(int from, int to) {
        if (from < 0 || from >= keys.size()) return this;
        List<Key> next = new ArrayList<>(keys);
        Key moved = next.remove(from);
        next.add(Math.clamp(to, 0, next.size()), moved);
        return new Chord(next);
    }

    /** The last key chosen, or null — what the one-key editor answers. */
    Key last() {
        return keys.isEmpty() ? null : keys.getLast();
    }

    boolean contains(Key key) {
        return keys.contains(key);
    }

    Optional<Combo> combo() {
        return keys.isEmpty() ? Optional.empty() : Optional.of(new Combo(keys));
    }
}
