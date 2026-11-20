// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

/**
 * Where an input of a screen stands for exploring, as {@link ScreenGraph#status} reads it off the map (spec
 * {@code 2026-11-20-explore-screen-done-design.md}): the run, the assistant's tools and the window all read this one.
 */
public enum InputStatus {

    /** A way from it is in the map, it is the screen's way back, or it goes to a screen mapped already. */
    LINKED("linked", "Linked"),
    /** Pressed, and the screen stayed: its effect is recorded. */
    STAYS("stays", "Stays"),
    /** Never done, spending a fact, or locked: not pressed, and none the less accounted for. */
    NOT_PRESSED("not_pressed", "Not pressed"),
    /** Pressed, and the press did not take ({@code Input.failed}). */
    FAILED("failed", "Failed"),
    /** None of the others: still to press. */
    UNTRIED("untried", "Untried");

    private final String id;
    private final String displayName;

    InputStatus(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Whether it is still to press, so its screen is not done. */
    public boolean counts() {
        return this == FAILED || this == UNTRIED;
    }

    /** The status {@code id} names; {@link #UNTRIED} for none. */
    public static InputStatus fromId(String id) {
        for (InputStatus status : values()) if (status.id.equals(id)) return status;
        return UNTRIED;
    }
}
