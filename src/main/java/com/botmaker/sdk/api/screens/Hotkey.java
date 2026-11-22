// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.function.BooleanSupplier;

/**
 * Keys a {@link Screen} answers — {@code Hotkey.of(Key.Q)}, or keys pressed together or held,
 * {@code Hotkey.of(Combo.of(Key.CTRL, Key.S))} (2026-10-22).
 *
 * @param combo        the keys pressed together, held as long as the combo says
 * @param isTried      whether Discovery tried it and it opened no screen
 * @param isLeaving    whether it goes to a screen mapped already that it is no way into
 * @param isInfo       whether it only tells
 * @param verdict      whether a run exploring on its own may press it; {@link Verdict#NONE} when null
 * @param isOccasional whether it works only at times
 * @param locked       the picture proving it locked on this account; null when it is not
 * @param when         when it works, for one that comes and goes; null when not known
 * @param failed       why its last press did not take; null when none did
 */
public record Hotkey(Combo combo, boolean isTried, boolean isLeaving, boolean isInfo, Verdict verdict,
                     boolean isOccasional, ImageTemplate locked, BooleanSupplier when, String failed)
        implements Input {

    /** A key whose presses all took, or none was made. */
    public Hotkey(Combo combo, boolean isTried, boolean isLeaving, boolean isInfo, Verdict verdict,
                  boolean isOccasional, ImageTemplate locked, BooleanSupplier when) {
        this(combo, isTried, isLeaving, isInfo, verdict, isOccasional, locked, when, null);
    }

    public Hotkey {
        verdict = verdict == null ? Verdict.NONE : verdict;
        isOccasional = isOccasional || when != null;
    }

    /** {@code key} pressed. */
    public static Hotkey of(Key key) {
        return of(Combo.of(key));
    }

    /** {@code combo}'s keys pressed together, held as long as it says. */
    public static Hotkey of(Combo combo) {
        return new Hotkey(combo, false, false, false, null, false, null, null);
    }

    /** The one key it presses with no hold, as {@link #of(Key)} writes it; null for keys together or held. */
    public Key key() {
        return combo != null && combo.keys().size() == 1 && combo.hold().isZero() ? combo.keys().getFirst() : null;
    }

    /** Whether it presses {@code keys}' keys, in that order, held or not. */
    public boolean presses(Combo keys) {
        return combo != null && keys != null && combo.keys().equals(keys.keys());
    }

    @Override
    public Hotkey tried() {
        return new Hotkey(combo, true, isLeaving, isInfo, verdict, isOccasional, locked, when, null);
    }

    @Override
    public Hotkey failed(String why) {
        return new Hotkey(combo, isTried, isLeaving, isInfo, verdict, isOccasional, locked, when, why);
    }

    @Override
    public Hotkey leaving() {
        return new Hotkey(combo, isTried, true, isInfo, verdict, isOccasional, locked, when, failed);
    }

    @Override
    public Hotkey info() {
        return new Hotkey(combo, isTried, isLeaving, true, verdict, isOccasional, locked, when, failed);
    }

    @Override
    public Hotkey never(String why) {
        return verdict(Verdict.never(why));
    }

    @Override
    public Hotkey free() {
        return verdict(Verdict.FREE);
    }

    @Override
    public <G, T> Hotkey spends(Params.Of<G, T> fact) {
        return verdict(new Verdict.Spends(fact));
    }

    @Override
    public Hotkey verdict(Verdict verdict) {
        return new Hotkey(combo, isTried, isLeaving, isInfo, verdict, isOccasional, locked, when, failed);
    }

    @Override
    public Hotkey occasional() {
        return new Hotkey(combo, isTried, isLeaving, isInfo, verdict, true, locked, when, failed);
    }

    @Override
    public Hotkey locked(ImageTemplate proof) {
        return new Hotkey(combo, isTried, isLeaving, isInfo, verdict, isOccasional, proof, when, failed);
    }

    @Override
    public Hotkey when(BooleanSupplier condition) {
        return new Hotkey(combo, isTried, isLeaving, isInfo, verdict, isOccasional, locked, condition, failed);
    }

    /** This hotkey, pressing {@code combo} instead. */
    public Hotkey combo(Combo combo) {
        return new Hotkey(combo, isTried, isLeaving, isInfo, verdict, isOccasional, locked, when, failed);
    }

    @Override
    public String label() {
        return "key " + (combo == null ? "?" : combo.toString());
    }
}
