// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.Instance;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.capture.AutoCapture;
import com.botmaker.sdk.internal.plan.DoneKey;
import com.botmaker.sdk.internal.plan.DoneRecords;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * What a run that was dealt nothing claims before its first click (spec {@code 2026-11-26-run-places-design.md} §5): the
 * emulator instance it plays on, by the key the core's registry gives the SDK pool's hold of that instance
 * ({@code RunRegistry.Hold.key}: the SDK's coordinate, the parameter's id, the instance as the grammar writes it). So a
 * ▶ Run on an instance a pooled run plays is refused, and a deal skips an instance a lone run plays.
 */
public final class PlaceClaims {

    private PlaceClaims() {}

    /** The claim key of {@code instance}, the SDK pool's hold of {@code Place.android(instance)}. */
    public static String key(String instance) {
        return DoneRecords.GROUP + ":" + DoneRecords.ARTIFACT + "/" + SdkValues.PLACE.id() + "/"
                + Place.class.getName() + ".android(" + quoted(instance) + ")";
    }

    /**
     * The instance a run that {@code decision} answered for claims, or empty: none when the host named it
     * ({@code botmaker.capture.emulator}, a host that holds it, such as the Emulators window's sign-in), when it was
     * dealt one (its row holds it), or when it reads no emulator.
     */
    public static Optional<String> toClaim(AutoCapture.Inputs in, AutoCapture.Decision decision) {
        if (in.emulatorProperty() != null && !in.emulatorProperty().isBlank()) return Optional.empty();
        if (!AutoCapture.android(in.dealt()).isEmpty()) return Optional.empty();
        if (!(decision instanceof AutoCapture.Decision.Fixed fixed)) return Optional.empty();
        String instance = DoneKey.instance(fixed.source());
        return instance.isEmpty() ? Optional.empty() : Optional.of(instance);
    }

    /**
     * The key a run of the SDK before places holds {@code instance} by ({@code Instance.named(instance)} under
     * {@code SdkValue.Id.INSTANCE}), claimed beside {@link #key} for one release so a run of either SDK sees the
     * other's. It goes with {@code Instance}.
     */
    @SuppressWarnings("removal")
    static String oldKey(String instance) {
        return DoneRecords.GROUP + ":" + DoneRecords.ARTIFACT + "/" + SdkValue.Id.class.getName() + "."
                + SdkValue.Id.INSTANCE.name() + "/" + Instance.class.getName() + ".named(" + quoted(instance) + ")";
    }

    /**
     * Claims {@code instance} through {@code claims} ({@code Claims::claim} in a run), by its key and its key before
     * places, or throws the words that stop the run: another run plays there. The contract's claim says no holder,
     * and answers no too when the live runs cannot be read, so the words name no holder and say both.
     */
    public static void claim(String instance, Predicate<String> claims) {
        if (!claims.test(key(instance)) || !claims.test(oldKey(instance))) {
            throw new IllegalStateException(instance + " is played by another run, or this PC's runs could not be"
                    + " read; stop it, or run on another instance");
        }
    }

    /** {@code value} as a Java string literal, written as the core's canonical form writes one. */
    static String quoted(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                default -> {
                    if (c < 0x20 || c > 0x7e) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
