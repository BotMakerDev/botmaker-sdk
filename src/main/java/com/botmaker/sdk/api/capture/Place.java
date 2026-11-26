// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.capture;

/**
 * Where a run plays (2026-11-26, spec {@code 2026-11-26-run-places-design.md} §1): an emulator instance or phone, this
 * PC's desktop, one of its private displays, or one of its game VMs. {@code Sdk.place()} is a parameter, and several
 * runs at once are each dealt a place into it. A place is a fact of a PC, never of the bot.
 *
 * <p>Each place names its PC, {@value #THIS_PC} for now; a later release connects several. {@code Place.android("")}
 * names no instance: the capture source's own is used, as a run did before places.
 */
public final class Place {

    /** The PC every place is on until BotMaker connects several. */
    public static final String THIS_PC = "this-pc";

    /** What kind of place a run plays on. */
    public enum Kind {
        /** An emulator instance or a phone: one run at a time, its own pointer. */
        ANDROID("android", "Emulator or phone"),
        /** The PC's own desktop: several runs at once, and the user's pointer. */
        DESKTOP("desktop", "This desktop"),
        /** A private display of the PC (Linux): one run, its own pointer. */
        PRIVATE_DISPLAY("private-display", "Private display"),
        /** A game VM of the PC (Windows): one run, its own pointer. */
        VM("vm", "Game VM"),
        /** A kind this release does not know. */
        UNKNOWN("unknown", "Unknown");

        private final String id;
        private final String displayName;

        Kind(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        /** The kind as a place's key spells it. */
        public String id() {
            return id;
        }

        /** The kind for a person. */
        public String displayName() {
            return displayName;
        }

        /** The kind whose {@link #id()} is {@code id}; {@link #UNKNOWN} for any other. */
        public static Kind fromId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) return kind;
            }
            return UNKNOWN;
        }
    }

    private final Kind kind;
    private final String pc;
    private final String name;
    private final int display;

    private Place(Kind kind, String name, int display) {
        this.kind = kind;
        this.pc = THIS_PC;
        this.name = name == null ? "" : name.strip();
        this.display = display;
    }

    /** The emulator instance or phone {@code name} names, by its reference; {@code ""} for none. */
    public static Place android(String name) {
        return new Place(Kind.ANDROID, name, 0);
    }

    /** This PC's own desktop. */
    public static Place desktop() {
        return new Place(Kind.DESKTOP, "", 0);
    }

    /** This PC's private display {@code display}, counted from 1. */
    public static Place privateDisplay(int display) {
        if (display < 1) throw new IllegalArgumentException("a private display is counted from 1: " + display);
        return new Place(Kind.PRIVATE_DISPLAY, "", display);
    }

    /** This PC's game VM named {@code name}. */
    public static Place vm(String name) {
        return new Place(Kind.VM, name, 0);
    }

    /** What kind of place this is. */
    public Kind kind() {
        return kind;
    }

    /** The PC the place is on, {@value #THIS_PC} for now. */
    public String pc() {
        return pc;
    }

    /** The instance's or VM's name, stripped; {@code ""} for the desktop and a private display. */
    public String name() {
        return name;
    }

    /** A private display's number, counted from 1; {@code 0} for any other place. */
    public int display() {
        return display;
    }

    /** Whether this is an emulator instance or phone that names one: a run moves its capture there. */
    public boolean namesAndroid() {
        return kind == Kind.ANDROID && !name.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Place that && kind == that.kind && pc.equals(that.pc) && name.equals(that.name)
                && display == that.display;
    }

    @Override
    public int hashCode() {
        return ((kind.hashCode() * 31 + pc.hashCode()) * 31 + name.hashCode()) * 31 + display;
    }

    /** The place's key: its PC, its kind and its name or number, {@code this-pc/android/Pixel 10}. */
    @Override
    public String toString() {
        String key = pc + "/" + kind.id();
        if (kind == Kind.PRIVATE_DISPLAY) return key + "/" + display;
        return name.isEmpty() ? key : key + "/" + name;
    }
}
