// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import java.util.Optional;

/**
 * What a run explores toward (spec 2026-11-24-explore-agents-design § Goal-directed exploration): {@code text} in
 * plain words, and optionally {@code screen}, a screen's name that ends the run by a code check once the run is on
 * it. Not the bot plan's {@code Goal}.
 */
public record ExploreGoal(String text, String screen) {

    public ExploreGoal {
        text = text == null ? "" : text.strip();
        screen = screen == null || screen.isBlank() ? null : screen.strip();
    }

    /** No goal: the run builds the whole map. */
    public static final ExploreGoal NONE = new ExploreGoal("", null);

    /** Whether there is a goal. */
    public boolean given() {
        return !text.isEmpty() || screen != null;
    }

    /** The screen that ends the run once reached; empty without one. */
    public Optional<String> goalScreen() {
        return Optional.ofNullable(screen);
    }

    /** What the journal and a witness call it: its words, else its screen. */
    public String said() {
        return text.isEmpty() ? screen == null ? "" : screen : text;
    }
}
