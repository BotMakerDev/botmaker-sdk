package com.botmaker.sdk.api.screens;

import java.util.Arrays;
import java.util.Locale;

/**
 * How a {@link Button} is pressed: clicked, as most are, or double-clicked, right-clicked, held, or only pointed at
 * for what shows on hover (2026-10-22). An emulator takes a click, a double tap and a long press; a right or middle
 * click is a tap there, and a hover does nothing.
 */
public enum Gesture {
    /** One left click, as a button usually is pressed. */
    CLICK("Click"),
    /** Two quick left clicks: an item opened from a list, a file in a launcher. */
    DOUBLE("Double click"),
    /** One right click: a context menu. */
    RIGHT("Right click"),
    /** One middle click. */
    MIDDLE("Middle click"),
    /** The left button held down, {@link Button#holdTime()}, then let go: a charge, a long press. */
    HOLD("Hold"),
    /** The pointer moved onto it, pressing nothing: a menu or a tip that opens on hover. */
    HOVER("Hover"),
    /** A gesture this SDK does not know, pressed as a click. */
    UNKNOWN("Unknown");

    private final String displayName;

    Gesture(String displayName) {
        this.displayName = displayName;
    }

    /** How a tool names it: {@code double}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** How a window shows it: {@code Double click}. */
    public String displayName() {
        return displayName;
    }

    /** The gesture a tool names {@code id}; {@link #UNKNOWN} for none. */
    public static Gesture fromId(String id) {
        return Arrays.stream(values()).filter(g -> g.id().equals(id)).findFirst().orElse(UNKNOWN);
    }
}
