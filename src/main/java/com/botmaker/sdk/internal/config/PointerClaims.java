// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.capture.Place;

import java.util.List;
import java.util.function.Predicate;

/**
 * This desktop's mouse and keyboard, which one run at a time takes over (spec {@code 2026-11-26-run-places-design.md}
 * §3, §4): the key the SDK pool holds beside {@code Place.desktop()} for a run that takes over, and the key a run
 * claims before it escalates to the real devices. Several runs share this desktop while they post their input; none
 * shares the pointer.
 */
public final class PointerClaims {

    private PointerClaims() {}

    /** The key of this PC's desktop pointer, {@code pointer:this-pc/desktop}. */
    public static final String KEY = key(Place.desktop());

    /** The key of {@code desktop}'s pointer: its PC's, {@code pointer:<pc>/desktop}. */
    static String key(Place desktop) {
        return "pointer:" + desktop.pc() + "/desktop";
    }

    /**
     * The keys a run dealt {@code place} holds alone: the pointer for this desktop when the run takes over, none for
     * any other place, whose display or Android device is the run's own.
     */
    public static List<String> heldWith(Place place, boolean takesOver) {
        return place.kind() == Place.Kind.DESKTOP && takesOver ? List.of(key(place)) : List.of();
    }

    /**
     * Claims the pointer through {@code claims} ({@code Claims::claim} in a run), or throws the words that stop the
     * run.
     * A run dealt this desktop to take over holds it already, so its claim is granted. The contract's claim says no
     * holder, and answers no too when the live runs cannot be read, so the words name no holder and say both.
     */
    public static void claim(Predicate<String> claims) {
        if (!claims.test(KEY)) {
            throw new IllegalStateException("This run now needs the mouse and keyboard, which another run holds (or"
                    + " this PC's runs could not be read); give it a private display or run it alone");
        }
    }
}
