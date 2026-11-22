// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.screens;

import java.util.Locale;

/**
 * The SDK's own meanings of a button's {@link Look}: as it usually looks, picked, or greyed out. Every other meaning
 * is the bot's, a constant of its {@code States} ({@link ButtonState}, 2026-11-02). A {@link Way} can need one
 * ({@link Way#whenLooks}), so a button leads where its look says.
 */
public enum State implements ButtonState {
    /** As it usually looks: the picture the input was cut as. */
    NORMAL("Normal"),
    /** Greyed out or dimmed: pressing it does nothing now, so a run never presses it. */
    DISABLED("Disabled"),
    /** Picked: the tab or toggle that is on. */
    SELECTED("Selected");

    private final String displayName;

    State(String displayName) {
        this.displayName = displayName;
    }

    /** Whether a button showing it is never pressed: disabled. */
    @Override
    public boolean blocks() {
        return this == DISABLED;
    }

    /** How a tool names it: {@code disabled}. */
    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** How a window shows it: {@code Disabled}. */
    @Override
    public String displayName() {
        return displayName;
    }
}
