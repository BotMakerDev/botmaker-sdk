// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.capture.Place;

import java.util.Optional;

/**
 * A place's key read back, and a place said for a person (claimed accounts, run-places phase 7c). The key is what
 * {@link Place#toString()} writes, {@code this-pc/android/MEmu_1}: the book keeps an account's {@code playsAt} so.
 */
public final class PlaceKeys {

    private PlaceKeys() {}

    /**
     * The place {@code key} names; empty for a blank key, one of another PC, a kind this build does not know, or a
     * private display that is no number from 1.
     */
    public static Optional<Place> parse(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        String[] parts = key.strip().split("/", 3);
        if (parts.length < 2 || !parts[0].equals(Place.THIS_PC)) return Optional.empty();
        String rest = parts.length == 3 ? parts[2] : "";
        return switch (Place.Kind.fromId(parts[1])) {
            case ANDROID -> Optional.of(Place.android(rest));
            case DESKTOP -> rest.isEmpty() ? Optional.of(Place.desktop()) : Optional.empty();
            case VM -> rest.isBlank() ? Optional.empty() : Optional.of(Place.vm(rest));
            case PRIVATE_DISPLAY -> display(rest).map(Place::privateDisplay);
            case UNKNOWN -> Optional.empty();
        };
    }

    /** A place for a person: an instance by its name, the desktop, a private display by its number, a VM by name. */
    public static String said(Place place) {
        return switch (place.kind()) {
            case ANDROID -> place.name().isEmpty() ? place.kind().displayName() : place.name();
            case DESKTOP -> place.kind().displayName();
            case PRIVATE_DISPLAY -> place.kind().displayName() + " " + place.display();
            case VM, UNKNOWN -> (place.kind().displayName() + " " + place.name()).strip();
        };
    }

    private static Optional<Integer> display(String number) {
        try {
            int n = Integer.parseInt(number);
            return n >= 1 ? Optional.of(n) : Optional.empty();
        } catch (NumberFormatException notOne) {
            return Optional.empty();
        }
    }
}
