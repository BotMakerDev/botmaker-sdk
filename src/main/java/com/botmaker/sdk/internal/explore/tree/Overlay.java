// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore.tree;

import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import com.botmaker.shared.web.Role;
import com.botmaker.shared.web.WebTree;

import java.util.List;
import java.util.Optional;

/**
 * What is open over a website's page (web discovery spec §2): an open dialog, a menu shown, or the selected tab. A
 * page's route and its overlay are its screen; the same route with another overlay is another screen. A form's step
 * is not told yet: it needs the form probe (phase 7).
 *
 * @param kind what is open
 * @param name its accessible name, or its first heading when it has none; {@code ""} for {@link Kind#NONE}
 */
public record Overlay(Kind kind, String name) {

    /** Nothing open over the page. */
    public static final Overlay NONE = new Overlay(Kind.NONE, "");

    public Overlay {
        kind = kind == null ? Kind.UNKNOWN : kind;
        name = name == null ? "" : name.strip();
    }

    /** What an overlay is. */
    public enum Kind {
        NONE("none", "Nothing open"),
        DIALOG("dialog", "Dialog"),
        MENU("menu", "Menu"),
        TAB("tab", "Tab"),
        UNKNOWN("unknown", "Unknown");

        private final String id;
        private final String displayName;

        Kind(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

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

    /**
     * What is open over the page {@code tree} holds: a dialog first, then a menu, then a selected tab; {@link #NONE}
     * for an app's tree, which tells none of them.
     */
    public static Overlay of(UiTree tree) {
        UiNode menu = null;
        UiNode tab = null;
        for (UiNode node : tree.all()) {
            Optional<Role> role = WebTree.roleOf(node.className());
            if (role.isEmpty()) continue;
            switch (role.get()) {
                case DIALOG, ALERT_DIALOG -> {
                    return new Overlay(Kind.DIALOG, named(node));
                }
                case MENU -> {
                    if (menu == null) menu = node;
                }
                case TAB -> {
                    if (tab == null && node.has(Trait.SELECTED)) tab = node;
                }
                default -> {
                }
            }
        }
        if (menu != null) return new Overlay(Kind.MENU, named(menu));
        if (tab != null) return new Overlay(Kind.TAB, named(tab));
        return NONE;
    }

    /** Whether nothing is open. */
    public boolean none() {
        return kind == Kind.NONE;
    }

    /** One line: {@code dialog "Talk to us"}. */
    public String line() {
        return none() ? kind.id() : kind.id() + " \"" + name + "\"";
    }

    private static String named(UiNode node) {
        if (!node.label().isBlank()) return node.label();
        for (UiNode inside : new UiTree(List.of(node)).all()) {
            if (WebTree.roleOf(inside.className()).orElse(Role.UNKNOWN) == Role.HEADING && !inside.label().isBlank()) {
                return inside.label();
            }
        }
        return "";
    }
}
