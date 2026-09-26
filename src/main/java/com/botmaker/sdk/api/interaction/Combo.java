package com.botmaker.sdk.api.interaction;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Keys pressed together — {@code Ctrl+Shift+S} — as one value, for {@link Keyboard#combo(Combo)}.
 *
 * <p>Held in the order given and released in reverse. The editor writes the modifiers first (Ctrl, Alt, Shift,
 * Meta, then the one other key), which is the order a person presses them in; this type does not reorder, so a
 * combo a bot wrote by hand does what it says.
 *
 * <p>A combo of modifiers alone is a combo ({@code Combo.of(Key.CTRL)}); an empty one is refused.
 */
@Palette(category = "interaction", categoryLabel = "Interaction", order = 107)
@Hidden("a value type: a combination a bot passes to Keyboard.combo, never a menu entry of its own")
public record Combo(List<Key> keys) {

    public Combo {
        keys = List.copyOf(Objects.requireNonNull(keys, "keys"));   // copyOf refuses a null key
        if (keys.isEmpty()) throw new IllegalArgumentException("a combo needs at least one key");
    }

    /** {@code Combo.of(Key.CTRL, Key.S)}. */
    public static Combo of(Key... keys) {
        return new Combo(List.of(Objects.requireNonNull(keys, "keys")));
    }

    /** The caps joined with {@code +}: {@code Ctrl+Shift+S}. */
    @Override
    public String toString() {
        return keys.stream().map(Key::label).collect(Collectors.joining("+"));
    }
}
