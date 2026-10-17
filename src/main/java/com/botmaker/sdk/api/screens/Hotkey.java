package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.function.BooleanSupplier;

/**
 * A key a {@link Screen} answers — {@code Hotkey.of(Key.Q)}.
 *
 * @param key          the key pressed
 * @param isTried      whether Discovery tried it and it opened no screen
 * @param isLeaving    whether it goes to a screen mapped already that it is no way into
 * @param isInfo       whether it only tells
 * @param never        why it is never done; null when it may be
 * @param comesAndGoes whether it works only at times
 * @param lock         the picture proving it locked on this account; null when it is not
 * @param when         when it works, for one that comes and goes; null when not known
 */
public record Hotkey(Key key, boolean isTried, boolean isLeaving, boolean isInfo, String never,
                     boolean comesAndGoes, ImageTemplate lock, BooleanSupplier when) implements Input {

    public Hotkey {
        comesAndGoes = comesAndGoes || when != null;
    }

    /** {@code key} pressed. */
    public static Hotkey of(Key key) {
        return new Hotkey(key, false, false, false, null, false, null, null);
    }

    @Override
    public Hotkey tried() {
        return new Hotkey(key, true, isLeaving, isInfo, never, comesAndGoes, lock, when);
    }

    @Override
    public Hotkey leaves() {
        return new Hotkey(key, isTried, true, isInfo, never, comesAndGoes, lock, when);
    }

    @Override
    public Hotkey info() {
        return new Hotkey(key, isTried, isLeaving, true, never, comesAndGoes, lock, when);
    }

    @Override
    public Hotkey never(String why) {
        return new Hotkey(key, isTried, isLeaving, isInfo, why, comesAndGoes, lock, when);
    }

    @Override
    public Hotkey sometimes() {
        return new Hotkey(key, isTried, isLeaving, isInfo, never, true, lock, when);
    }

    @Override
    public Hotkey locked(ImageTemplate lock) {
        return new Hotkey(key, isTried, isLeaving, isInfo, never, comesAndGoes, lock, when);
    }

    @Override
    public Hotkey when(BooleanSupplier condition) {
        return new Hotkey(key, isTried, isLeaving, isInfo, never, comesAndGoes, lock, condition);
    }

    @Override
    public String label() {
        return "key " + (key == null ? "?" : key.label());
    }
}
