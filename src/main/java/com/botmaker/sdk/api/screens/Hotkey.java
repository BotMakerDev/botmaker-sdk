package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.function.BooleanSupplier;

/**
 * A key a {@link Screen} answers — {@code Hotkey.of(Key.Q)}.
 *
 * @param key          the key pressed
 * @param did          what it did when tried, opening nothing; null when untried or when it opened a screen
 * @param purpose      what it is for; null when not named yet
 * @param never        why it is never done; null when it may be
 * @param comesAndGoes whether it works only at times
 * @param lock         the picture proving it locked on this account; null when it is not
 * @param when         when it works, for one that comes and goes; null when not known
 */
public record Hotkey(Key key, Did did, Purpose purpose, String never, boolean comesAndGoes, ImageTemplate lock,
                     BooleanSupplier when) implements Input {

    public Hotkey {
        comesAndGoes = comesAndGoes || when != null;
    }

    /** {@code key} pressed. */
    public static Hotkey of(Key key) {
        return new Hotkey(key, null, null, null, false, null, null);
    }

    @Override
    public Hotkey did(Did did) {
        return new Hotkey(key, did, purpose, never, comesAndGoes, lock, when);
    }

    @Override
    public Hotkey purpose(Purpose purpose) {
        return new Hotkey(key, did, purpose, never, comesAndGoes, lock, when);
    }

    @Override
    public Hotkey never(String why) {
        return new Hotkey(key, did, purpose, why, comesAndGoes, lock, when);
    }

    @Override
    public Hotkey sometimes() {
        return new Hotkey(key, did, purpose, never, true, lock, when);
    }

    @Override
    public Hotkey locked(ImageTemplate lock) {
        return new Hotkey(key, did, purpose, never, comesAndGoes, lock, when);
    }

    @Override
    public Hotkey when(BooleanSupplier condition) {
        return new Hotkey(key, did, purpose, never, comesAndGoes, lock, condition);
    }

    @Override
    public String label() {
        return "key " + (key == null ? "?" : key.label());
    }
}
