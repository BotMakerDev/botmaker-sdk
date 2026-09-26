package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the combination editor holds while it is open: modifiers that toggle, and at most one other key. Written
 * back modifiers first, in the order a person presses them (Ctrl, Alt, Shift, Meta), so every combo this editor
 * writes reads the same way. Pure: no JavaFX.
 */
record Chord(Set<Key> modifiers, Key main) {

    static final List<Key> MODIFIER_ORDER = List.of(Key.CTRL, Key.ALT, Key.SHIFT, Key.META);
    static final Chord EMPTY = new Chord(Set.of(), null);

    Chord {
        modifiers = Set.copyOf(modifiers);
    }

    static boolean modifier(Key key) {
        return MODIFIER_ORDER.contains(key);
    }

    /** A combo read back: its modifiers, and its last other key (a hand-written combo may hold several). */
    static Chord of(Combo combo) {
        Set<Key> mods = EnumSet.noneOf(Key.class);
        Key main = null;
        for (Key key : combo.keys()) {
            if (modifier(key)) mods.add(key);
            else main = key;
        }
        return new Chord(mods, main);
    }

    /** A click on a cap: a modifier toggles; another key replaces the main one, or clears it if it is that one. */
    Chord click(Key key) {
        if (modifier(key)) {
            Set<Key> mods = EnumSet.noneOf(Key.class);
            mods.addAll(modifiers);
            if (!mods.remove(key)) mods.add(key);
            return new Chord(mods, main);
        }
        return new Chord(modifiers, key == main ? null : key);
    }

    /** A real keystroke, with the modifiers held while it was pressed: the whole chord at once. */
    static Chord pressed(boolean ctrl, boolean alt, boolean shift, boolean meta, Key key) {
        Set<Key> mods = EnumSet.noneOf(Key.class);
        if (ctrl) mods.add(Key.CTRL);
        if (alt) mods.add(Key.ALT);
        if (shift) mods.add(Key.SHIFT);
        if (meta) mods.add(Key.META);
        if (modifier(key)) {
            mods.add(key);
            return new Chord(mods, null);
        }
        return new Chord(mods, key);
    }

    Optional<Combo> combo() {
        List<Key> keys = new ArrayList<>();
        for (Key m : MODIFIER_ORDER) {
            if (modifiers.contains(m)) keys.add(m);
        }
        if (main != null) keys.add(main);
        return keys.isEmpty() ? Optional.empty() : Optional.of(new Combo(keys));
    }
}
