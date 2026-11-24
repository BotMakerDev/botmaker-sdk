// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What code checks before a run's goal counts as reached (spec 2026-11-24-explore-agents-design § Goal-directed
 * exploration): never an agent's word alone. The goal's screen, when the run was given one, is the one the frame is
 * on; else a mark the Reader captured, a picture or a text, matches the frame again.
 */
final class Witness {

    private Witness() {}

    /** A check of one frame. */
    sealed interface Check {
        /** The frame is on {@code name}, a screen of the map. */
        record ScreenOnMap(String name) implements Check {}

        /** The frame shows {@code mark}: a picture of that name matches it, or its text holds it. */
        record PictureOrText(String mark) implements Check {}
    }

    /**
     * A frame as code sees it: {@code id} where it was kept (blank: not kept), {@code screen} the map's screen it is
     * on, {@code onMap} whether a name is a screen of the map, its {@code texts}, and whether a picture of a name
     * matches it.
     */
    record Frame(String id, Optional<String> screen, Predicate<String> onMap, List<String> texts,
                 Predicate<String> picture) {
        Frame {
            id = id == null ? "" : id;
            texts = texts == null ? List.of() : List.copyOf(texts);
        }
    }

    /** Whether {@code check} holds on {@code frame}. */
    static boolean holds(Check check, Frame frame) {
        return how(check, frame).isPresent();
    }

    /** How {@code check} held on {@code frame}; empty when it did not. */
    static Optional<WitnessCheck> how(Check check, Frame frame) {
        return switch (check) {
            case Check.ScreenOnMap(String name) -> name != null && !name.isBlank() && frame.onMap().test(name)
                    && frame.screen().filter(name::equals).isPresent()
                    ? Optional.of(WitnessCheck.SCREEN_ON_MAP) : Optional.empty();
            case Check.PictureOrText(String mark) -> {
                if (mark == null || mark.isBlank()) yield Optional.empty();
                if (frame.picture().test(mark)) yield Optional.of(WitnessCheck.PICTURE);
                String wanted = norm(mark);
                yield frame.texts().stream().anyMatch(t -> norm(t).contains(wanted))
                        ? Optional.of(WitnessCheck.TEXT) : Optional.empty();
            }
        };
    }

    /** {@code text} lower case, its spaces made one. */
    private static String norm(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
