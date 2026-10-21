package com.botmaker.sdk.api.input;


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Keys pressed together — {@code Ctrl+Shift+S} — as one value, for {@link Keyboard#combo(Combo)}.
 *
 * <p>Held in the order given and released in reverse. The editor writes the keys in the order they were picked;
 * this type does not reorder, so a combo a bot wrote by hand does what it says.
 *
 * <p><b>A combo may be held</b> (2026-09-27): every key goes down, the combo waits {@link #hold()}, then the keys
 * come up. A game that ignores a press shorter than a frame needs this. No hold is the default and is written
 * {@code Combo.of(Key.CTRL, Key.S)}; a hold is written on it, {@code Combo.of(Key.CTRL, Key.S).held(
 * Duration.ofMillis(200))}.
 *
 * <p>A combo of modifiers alone is a combo ({@code Combo.of(Key.CTRL)}); an empty one is refused, and so is a
 * negative hold.
 */
public record Combo(List<Key> keys, Duration hold) {

    public Combo {
        keys = List.copyOf(Objects.requireNonNull(keys, "keys"));   // copyOf refuses a null key
        if (keys.isEmpty()) throw new IllegalArgumentException("a combo needs at least one key");
        Objects.requireNonNull(hold, "hold");
        if (hold.isNegative()) throw new IllegalArgumentException("a combo cannot be held for " + hold);
    }

    /** {@code keys}, pressed and released with no hold between. */
    public Combo(List<Key> keys) {
        this(keys, Duration.ZERO);
    }

    /** {@code Combo.of(Key.CTRL, Key.S)}. */
    public static Combo of(Key... keys) {
        return new Combo(List.of(Objects.requireNonNull(keys, "keys")));
    }

    /** These keys, held down for {@code hold} before they are released: {@code Combo.of(Key.W).held(…)}. */
    public Combo held(Duration hold) {
        return new Combo(keys, hold);
    }

    /**
     * The keys {@code text} names, joined with {@code +} as {@link #caps()} writes them, each as {@link Key#named}
     * reads it: {@code Ctrl+S}, {@code alt + tab}, {@code Q}. Empty when one names no key; never held.
     */
    public static Optional<Combo> named(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String[] parts = text.split("\\+", -1);
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            // A key whose label holds the joiner (Num +) takes the parts after it until it names one.
            String name = parts[i];
            Optional<Key> key = Key.named(name.strip());
            while (key.isEmpty() && i + 1 < parts.length) {
                name = name + "+" + parts[++i];
                key = Key.named(name.strip());
            }
            if (key.isEmpty()) return Optional.empty();
            keys.add(key.get());
        }
        return keys.isEmpty() ? Optional.empty() : Optional.of(new Combo(keys));
    }

    /** The caps joined with {@code +}, with no hold: {@code Ctrl+S}. */
    public String caps() {
        return keys.stream().map(Key::label).collect(Collectors.joining("+"));
    }

    /** The caps joined with {@code +}, then the hold when there is one: {@code Ctrl+S (hold 200 ms)}. */
    @Override
    public String toString() {
        return hold.isZero() ? caps() : caps() + " (hold " + hold.toMillis() + " ms)";
    }
}
